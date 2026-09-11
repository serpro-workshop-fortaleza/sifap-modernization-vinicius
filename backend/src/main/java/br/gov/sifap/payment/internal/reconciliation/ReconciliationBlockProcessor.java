package br.gov.sifap.payment.internal.reconciliation;

import br.gov.sifap.payment.ReturnCode;
import br.gov.sifap.payment.event.PaymentDivergenceDetected;
import br.gov.sifap.payment.event.PaymentReconciled;
import br.gov.sifap.payment.internal.Payment;
import br.gov.sifap.payment.internal.PaymentRepository;
import br.gov.sifap.payment.internal.reconciliation.PaymentMatcher.Match;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processamento de um bloco do arquivo de retorno.
 *
 * <p>{@code REQUIRES_NEW}: bloco confirmado permanece confirmado. A retomada do
 * {@code REQ-REC-015} nao reaplica o que ja foi conciliado porque a marca esta no proprio
 * pagamento, e nao em um contador de memoria.
 *
 * <p>O {@code BATCHCON} confirma cada registro individualmente
 * ({@code BATCHCON.NSP:210}) e, quando falha, {@code BACKOUT TRANSACTION} desfaz apenas o
 * ultimo. Tudo antes permanece, e nada registra onde parou.
 */
@Service
class ReconciliationBlockProcessor {

    /** {@code BATCHCON.NSP:191}: diferenca de ate um centavo e tolerada. */
    static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final PaymentRepository payments;
    private final PaymentMatcher matcher;
    private final ReconciliationIssueRepository issues;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    ReconciliationBlockProcessor(
            PaymentRepository payments,
            PaymentMatcher matcher,
            ReconciliationIssueRepository issues,
            ApplicationEventPublisher publisher,
            Clock clock) {
        this.payments = payments;
        this.matcher = matcher;
        this.issues = issues;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void process(
            List<ParsedLine> block,
            Long fileId,
            String runId,
            String referencePeriod,
            Actor actor,
            ReconciliationTally tally) {

        List<AuditableEvent> events = new ArrayList<>(block.size());

        for (ParsedLine line : block) {
            switch (line) {
                case ParsedLine.Skipped ignored -> tally.recordSkipped();
                case ParsedLine.Rejected rejected -> {
                    tally.recordRead();
                    register(fileId, ReconciliationIssueType.VALOR_INVALIDO, rejected.cpf(),
                            referencePeriod, String.join("; ", rejected.reasons()), null, null);
                    tally.recordPending();
                }
                case ParsedLine.Parsed parsed -> {
                    tally.recordRead();
                    apply(parsed.record(), fileId, runId, referencePeriod, actor, tally, events);
                }
            }
        }

        if (!events.isEmpty()) {
            publisher.publishEvent(new AuditableEventBatch(events));
        }
    }

    private void apply(
            ReturnRecord record,
            Long fileId,
            String runId,
            String referencePeriod,
            Actor actor,
            ReconciliationTally tally,
            List<AuditableEvent> events) {

        Payment payment = switch (matcher.match(record, referencePeriod)) {
            case Match.Found found -> found.payment();
            case Match.NotFound ignored -> {
                register(fileId, ReconciliationIssueType.SEM_CORRESPONDENCIA, record.cpf(),
                        referencePeriod, "nenhum pagamento corresponde ao registro de retorno",
                        record.amount(), record.returnCode());
                tally.recordPending();
                yield null;
            }
            case Match.Ambiguous ambiguous -> {
                register(fileId, ReconciliationIssueType.AMBIGUIDADE, record.cpf(), referencePeriod,
                        ambiguous.candidates() + " pagamentos no mesmo periodo; nenhum foi atualizado",
                        record.amount(), record.returnCode());
                tally.recordPending();
                yield null;
            }
        };

        if (payment == null) {
            return;
        }

        // REQ-REC-015: a marca esta no pagamento, e por isso sobrevive a interrupcao.
        if (payment.reconciliationStatus().isPresent()) {
            tally.recordAlreadyReconciled();
            return;
        }

        Optional<ReturnCode> returnCode = ReturnCode.fromCode(record.returnCode());
        if (returnCode.isEmpty()) {
            register(fileId, ReconciliationIssueType.CODIGO_DESCONHECIDO, record.cpf(), referencePeriod,
                    "codigo de retorno fora do dominio: " + record.returnCode(),
                    record.amount(), record.returnCode());
            tally.recordPending();
            return;
        }

        BankCode bankCode;
        try {
            bankCode = BankCode.of(record.bankCode());
        } catch (IllegalArgumentException unknownBank) {
            register(fileId, ReconciliationIssueType.BANCO_DESCONHECIDO, record.cpf(), referencePeriod,
                    unknownBank.getMessage(), record.amount(), record.returnCode());
            tally.recordPending();
            return;
        }

        try {
            transition(payment, record, returnCode.get(), bankCode, runId, tally, events, actor);
        } catch (DomainRuleException invalidTransition) {
            register(fileId, ReconciliationIssueType.TRANSICAO_INVALIDA, record.cpf(), referencePeriod,
                    invalidTransition.getMessage(), record.amount(), record.returnCode());
            tally.recordPending();
        }
    }

    private void transition(
            Payment payment,
            ReturnRecord record,
            ReturnCode returnCode,
            BankCode bankCode,
            String runId,
            ReconciliationTally tally,
            List<AuditableEvent> events,
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
                    runId,
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
                runId,
                actor,
                clock.instant()));
    }

    private void register(
            Long fileId,
            ReconciliationIssueType type,
            String cpf,
            String referencePeriod,
            String detail,
            BigDecimal amount,
            String returnCode) {
        issues.save(new ReconciliationIssue(
                fileId, type, cpf, referencePeriod, null, amount, returnCode, detail, clock.instant()));
    }
}
