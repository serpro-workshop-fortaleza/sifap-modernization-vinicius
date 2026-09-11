package br.gov.sifap.payment.internal.reconciliation;

import br.gov.sifap.payment.ReconciliationResult;
import br.gov.sifap.payment.ReconciliationResult.ReconciliationIssueView;
import br.gov.sifap.payment.ReturnCode;
import br.gov.sifap.payment.event.PaymentDivergenceDetected;
import br.gov.sifap.payment.event.PaymentReconciled;
import br.gov.sifap.payment.event.ReconciliationCycleCompleted;
import br.gov.sifap.payment.internal.Payment;
import br.gov.sifap.payment.internal.PaymentRepository;
import br.gov.sifap.payment.internal.PayrollEventRecorder;
import br.gov.sifap.payment.internal.reconciliation.PaymentMatcher.Match;
import br.gov.sifap.shared.document.Cpf;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.AuditableEventBatch;
import br.gov.sifap.shared.exception.DomainRuleException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de conciliacao bancaria.
 *
 * <p>Atende {@code REQ-REC-001} a {@code REQ-REC-016}.
 *
 * <p>Diferente da folha, o arquivo de retorno cabe em uma transacao: sao ate 3,8 milhoes
 * de registros, mas a operacao por registro e uma atualizacao pontual, nao um calculo. O
 * que exige cuidado e outra coisa — o {@code BATCHCON} confirma cada registro
 * individualmente e, quando falha, {@code BACKOUT TRANSACTION} desfaz apenas o ultimo.
 */
@Service
public class ReconciliationService {

    /** {@code BATCHCON.NSP:191}: diferenca de ate um centavo e tolerada. */
    static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final ReconciliationFileRepository files;
    private final ReconciliationIssueRepository issues;
    private final PaymentRepository payments;
    private final PaymentMatcher matcher;
    private final ReturnFileLayout layout;
    private final PayrollEventRecorder recorder;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    ReconciliationService(
            ReconciliationFileRepository files,
            ReconciliationIssueRepository issues,
            PaymentRepository payments,
            PaymentMatcher matcher,
            ReturnFileLayout layout,
            PayrollEventRecorder recorder,
            ApplicationEventPublisher publisher,
            Clock clock) {
        this.files = files;
        this.issues = issues;
        this.payments = payments;
        this.matcher = matcher;
        this.layout = layout;
        this.recorder = recorder;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public ReconciliationResult reconcile(
            String declaredName, String referencePeriod, List<String> lines, Actor actor) {

        String sha256 = FileDigest.of(lines);
        ReconciliationFile file = openFile(sha256, declaredName, referencePeriod, actor);

        ReturnFileParser parser = new ReturnFileParser(layout);
        ReconciliationTally tally = new ReconciliationTally();
        List<AuditableEvent> events = new ArrayList<>();

        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            switch (parser.parse(line, lineNumber)) {
                case ParsedLine.Skipped ignored -> tally.recordSkipped();
                case ParsedLine.Rejected rejected -> {
                    tally.recordRead();
                    register(file, ReconciliationIssueType.VALOR_INVALIDO, rejected.cpf(), referencePeriod,
                            String.join("; ", rejected.reasons()), null, null);
                    tally.recordPending();
                }
                case ParsedLine.Parsed parsed -> {
                    tally.recordRead();
                    apply(parsed.record(), file, referencePeriod, actor, tally, events);
                }
            }
        }

        file.complete(tally.read());
        files.save(file);

        if (!events.isEmpty()) {
            publisher.publishEvent(new AuditableEventBatch(events));
        }
        recorder.record(new ReconciliationCycleCompleted(
                runIdOf(file),
                sha256,
                declaredName,
                referencePeriod,
                tally.read(),
                tally.reconciled(),
                tally.divergent(),
                tally.pending(),
                tally.confirmedTotal(),
                actor,
                clock.instant()));

        return toResult(file, tally);
    }

    /**
     * Abre o arquivo, recusando o que ja foi concluido.
     *
     * <p>{@code REQ-REC-003} e {@code REQ-REC-015}: um arquivo interrompido pode ser
     * retomado; um concluido, nao.
     */
    private ReconciliationFile openFile(
            String sha256, String declaredName, String referencePeriod, Actor actor) {
        Optional<ReconciliationFile> existing = files.findBySha256(sha256);

        if (existing.filter(ReconciliationFile::isCompleted).isPresent()) {
            throw new DomainRuleException(
                    "REQ-REC-003",
                    "arquivo de retorno ja processado em "
                            + existing.orElseThrow().processedAt()
                            + "; conteudo identico ao de " + existing.orElseThrow().declaredName());
        }

        return existing.orElseGet(() -> files.save(
                ReconciliationFile.start(sha256, declaredName, referencePeriod, actor.id(), clock)));
    }

    private void apply(
            ReturnRecord record,
            ReconciliationFile file,
            String referencePeriod,
            Actor actor,
            ReconciliationTally tally,
            List<AuditableEvent> events) {

        Payment payment = switch (matcher.match(record, referencePeriod)) {
            case Match.Found found -> found.payment();
            case Match.NotFound ignored -> {
                register(file, ReconciliationIssueType.SEM_CORRESPONDENCIA, record.cpf(), referencePeriod,
                        "nenhum pagamento corresponde ao registro de retorno",
                        record.amount(), record.returnCode());
                tally.recordPending();
                yield null;
            }
            case Match.Ambiguous ambiguous -> {
                register(file, ReconciliationIssueType.AMBIGUIDADE, record.cpf(), referencePeriod,
                        ambiguous.candidates() + " pagamentos no mesmo periodo; nenhum foi atualizado",
                        record.amount(), record.returnCode());
                tally.recordPending();
                yield null;
            }
        };

        if (payment == null) {
            return;
        }

        // REQ-REC-015: registro ja conciliado nao e reaplicado na retomada.
        if (payment.reconciliationStatus().isPresent()) {
            tally.recordSkipped();
            return;
        }

        Optional<ReturnCode> returnCode = ReturnCode.fromCode(record.returnCode());
        if (returnCode.isEmpty()) {
            // REQ-REC-008: o legado conta como conciliado antes de chegar aqui.
            register(file, ReconciliationIssueType.CODIGO_DESCONHECIDO, record.cpf(), referencePeriod,
                    "codigo de retorno fora do dominio: " + record.returnCode(),
                    record.amount(), record.returnCode());
            tally.recordPending();
            return;
        }

        BankCode bankCode;
        try {
            bankCode = BankCode.of(record.bankCode());
        } catch (IllegalArgumentException unknownBank) {
            register(file, ReconciliationIssueType.BANCO_DESCONHECIDO, record.cpf(), referencePeriod,
                    unknownBank.getMessage(), record.amount(), record.returnCode());
            tally.recordPending();
            return;
        }

        try {
            transition(payment, record, returnCode.get(), bankCode, tally, events, file, actor);
        } catch (DomainRuleException invalidTransition) {
            register(file, ReconciliationIssueType.TRANSICAO_INVALIDA, record.cpf(), referencePeriod,
                    invalidTransition.getMessage(), record.amount(), record.returnCode());
            tally.recordPending();
        }
    }

    private void transition(
            Payment payment,
            ReturnRecord record,
            ReturnCode returnCode,
            BankCode bankCode,
            ReconciliationTally tally,
            List<AuditableEvent> events,
            ReconciliationFile file,
            Actor actor) {

        String previousStatus = payment.status().name();
        BigDecimal difference = payment.amountNet().subtract(record.amount()).abs();

        if (difference.compareTo(TOLERANCE) > 0) {
            payment.markDivergent(record.amount(), bankCode.value(), returnCode.code(), clock);
            payments.save(payment);
            tally.recordDivergent();
            events.add(new PaymentDivergenceDetected(
                    String.valueOf(payment.id()),
                    payment.cpf(),
                    payment.amountNet(),
                    record.amount(),
                    returnCode.code(),
                    runIdOf(file),
                    actor,
                    clock.instant()));
            return;
        }

        if (difference.signum() > 0) {
            tally.recordWithinTolerance();
        }

        switch (returnCode) {
            case CREDITADO -> payment.confirmCredit(
                    record.amount(), record.paymentDate(), bankCode.value(), returnCode.code(), clock);
            case DEVOLVIDO -> payment.markReturned(
                    record.amount(), bankCode.value(), returnCode.code(), clock);
            case ESTORNADO -> payment.markReversed(
                    record.amount(), bankCode.value(), returnCode.code(), clock);
        }

        payments.save(payment);
        tally.recordReconciled(record.amount());
        events.add(new PaymentReconciled(
                String.valueOf(payment.id()),
                payment.cpf(),
                previousStatus,
                payment.status().name(),
                record.amount(),
                returnCode.code(),
                runIdOf(file),
                actor,
                clock.instant()));
    }

    private void register(
            ReconciliationFile file,
            ReconciliationIssueType type,
            String cpf,
            String referencePeriod,
            String detail,
            BigDecimal amount,
            String returnCode) {
        issues.save(new ReconciliationIssue(
                file.id(), type, cpf, referencePeriod, null, amount, returnCode, detail, clock.instant()));
    }

    /** Identificador curto da execucao; batch_run_id da trilha e VARCHAR(50). */
    private static String runIdOf(ReconciliationFile file) {
        return "REC-" + file.id();
    }

    private ReconciliationResult toResult(ReconciliationFile file, ReconciliationTally tally) {
        List<ReconciliationIssueView> views = issues.findByFileId(file.id()).stream()
                .map(issue -> new ReconciliationIssueView(
                        issue.issueType().name(), Cpf.mask(issue.cpf()), issue.detail()))
                .toList();

        return new ReconciliationResult(
                runIdOf(file),
                file.sha256(),
                file.referencePeriod(),
                tally.read(),
                tally.skipped(),
                tally.reconciled(),
                tally.withinTolerance(),
                tally.divergent(),
                tally.pending(),
                tally.confirmedTotal(),
                views);
    }
}
