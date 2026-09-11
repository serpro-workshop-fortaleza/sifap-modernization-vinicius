package br.gov.sifap.payment.event;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.Change;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Ciclo de conciliacao encerrado.
 *
 * <p>Atende {@code REQ-REC-014}. Identifica o arquivo pelo resumo do conteudo, e nao pelo
 * nome: {@code BATCHCON.NSP:139-141} registra que o nome digitado na tela e documentacao.
 */
public record ReconciliationCycleCompleted(
        String runId,
        String fileSha256,
        String declaredName,
        String referencePeriod,
        int recordsRead,
        int reconciled,
        int divergent,
        int pending,
        BigDecimal confirmedTotal,
        Actor actor,
        Instant occurredAt)
        implements AuditableEvent {

    @Override
    public AuditAction action() {
        return AuditAction.PROCESSAMENTO;
    }

    @Override
    public String entityType() {
        return "RECONCILIATION_FILE";
    }

    @Override
    public String entityId() {
        return runId;
    }

    @Override
    public Optional<String> batchRunId() {
        return Optional.of(runId);
    }

    @Override
    public Map<String, Change> changes() {
        return Map.of(
                "fileSha256", Change.created(fileSha256),
                "declaredName", Change.created(declaredName),
                "recordsRead", Change.created(String.valueOf(recordsRead)),
                "reconciled", Change.created(String.valueOf(reconciled)),
                "divergent", Change.created(String.valueOf(divergent)),
                "pending", Change.created(String.valueOf(pending)),
                "confirmedTotal", Change.created(confirmedTotal.toPlainString()));
    }
}
