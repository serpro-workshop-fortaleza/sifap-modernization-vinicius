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
 * Ciclo de folha concluido.
 *
 * <p>Origem no legado: {@code BATCHPGT.NSP:536-546}. Coexiste com {@link PaymentGenerated}
 * por exigencia do {@code REQ-AUD-010}: o legado grava apenas este, e nenhum pagamento
 * individual e rastreavel.
 */
public record PayrollCycleCompleted(
        String cycleId,
        String referencePeriod,
        int generated,
        int ignored,
        int rejected,
        BigDecimal totalGross,
        BigDecimal totalNet,
        Actor actor,
        Instant occurredAt)
        implements AuditableEvent {

    @Override
    public AuditAction action() {
        return AuditAction.PROCESSAMENTO;
    }

    @Override
    public String entityType() {
        return "PAYROLL_CYCLE";
    }

    @Override
    public String entityId() {
        return cycleId;
    }

    @Override
    public Optional<String> batchRunId() {
        return Optional.of(cycleId);
    }

    @Override
    public Map<String, Change> changes() {
        return Map.of(
                "generated", Change.created(String.valueOf(generated)),
                "ignored", Change.created(String.valueOf(ignored)),
                "rejected", Change.created(String.valueOf(rejected)),
                "totalGross", Change.created(totalGross.toPlainString()),
                "totalNet", Change.created(totalNet.toPlainString()));
    }
}
