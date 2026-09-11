package br.gov.sifap.payment.event;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.Change;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Remessa bancaria emitida; o total de controle fica registrado antes do envio. */
public record BankRemittanceIssued(
        String cycleId,
        String referencePeriod,
        int records,
        BigDecimal controlTotal,
        Actor actor,
        Instant occurredAt)
        implements AuditableEvent {

    @Override
    public AuditAction action() {
        return AuditAction.PROCESSAMENTO;
    }

    @Override
    public String entityType() {
        return "BANK_REMITTANCE";
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
                "records", Change.created(String.valueOf(records)),
                "controlTotal", Change.created(controlTotal.toPlainString()));
    }
}
