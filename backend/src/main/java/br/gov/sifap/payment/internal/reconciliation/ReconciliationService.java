package br.gov.sifap.payment.internal.reconciliation;

import br.gov.sifap.payment.ReconciliationResult;
import br.gov.sifap.payment.ReconciliationResult.ReconciliationIssueView;
import br.gov.sifap.payment.event.ReconciliationCycleCompleted;
import br.gov.sifap.payment.internal.PayrollEventRecorder;
import br.gov.sifap.shared.document.Cpf;
import br.gov.sifap.shared.event.Actor;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Ciclo de conciliacao bancaria.
 *
 * <p>Atende {@code REQ-REC-001} a {@code REQ-REC-016}.
 *
 * <p>Nao ha {@code @Transactional} aqui, e a ausencia e o que torna o
 * {@code REQ-REC-015} possivel. Uma transacao unica sobre o arquivo inteiro desfaria, na
 * falha, ate o registro do proprio arquivo — e a retomada nao teria de onde partir.
 */
@Service
public class ReconciliationService {

    /** Bloco de 1000: e o intervalo em que {@code BATCHCON.NSP:210} confirma. */
    static final int BLOCK_SIZE = 1_000;

    private final ReconciliationFileRegistry registry;
    private final ReconciliationBlockProcessor blockProcessor;
    private final ReconciliationIssueRepository issues;
    private final ReturnFileLayout layout;
    private final PayrollEventRecorder recorder;
    private final Clock clock;

    ReconciliationService(
            ReconciliationFileRegistry registry,
            ReconciliationBlockProcessor blockProcessor,
            ReconciliationIssueRepository issues,
            ReturnFileLayout layout,
            PayrollEventRecorder recorder,
            Clock clock) {
        this.registry = registry;
        this.blockProcessor = blockProcessor;
        this.issues = issues;
        this.layout = layout;
        this.recorder = recorder;
        this.clock = clock;
    }

    public ReconciliationResult reconcile(
            String declaredName, String referencePeriod, List<String> lines, Actor actor) {

        String sha256 = FileDigest.of(lines);
        ReconciliationFile file = registry.open(sha256, declaredName, referencePeriod, actor);
        String runId = runIdOf(file);

        ReturnFileParser parser = new ReturnFileParser(layout);
        ReconciliationTally tally = new ReconciliationTally();

        List<ParsedLine> block = new ArrayList<>(BLOCK_SIZE);
        int lineNumber = 0;

        try {
            for (String line : lines) {
                block.add(parser.parse(line, ++lineNumber));
                if (block.size() == BLOCK_SIZE) {
                    blockProcessor.process(block, file.id(), runId, referencePeriod, actor, tally);
                    block.clear();
                }
            }
            if (!block.isEmpty()) {
                blockProcessor.process(block, file.id(), runId, referencePeriod, actor, tally);
            }
        } catch (RuntimeException failure) {
            registry.interrupt(file.id(), tally.read());
            throw failure;
        }

        registry.complete(file.id(), tally.read());

        recorder.record(new ReconciliationCycleCompleted(
                runId,
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

        return toResult(runId, file, tally);
    }

    /** Identificador curto da execucao; {@code batch_run_id} da trilha e {@code VARCHAR(50)}. */
    private static String runIdOf(ReconciliationFile file) {
        return "REC-" + file.id();
    }

    private ReconciliationResult toResult(
            String runId, ReconciliationFile file, ReconciliationTally tally) {
        List<ReconciliationIssueView> views = issues.findByFileId(file.id()).stream()
                .map(issue -> new ReconciliationIssueView(
                        issue.issueType().name(), Cpf.mask(issue.cpf()), issue.detail()))
                .toList();

        return new ReconciliationResult(
                runId,
                file.sha256(),
                file.referencePeriod(),
                tally.read(),
                tally.skipped(),
                tally.reconciled(),
                tally.alreadyReconciled(),
                tally.withinTolerance(),
                tally.divergent(),
                tally.pending(),
                tally.confirmedTotal(),
                views);
    }
}
