package br.gov.sifap.payment.event;

import br.gov.sifap.payment.FactorType;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.Change;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Pagamento gerado.
 *
 * <p>Origem no legado: {@code BATCHPGT.NSP:488}. Atende o {@code REQ-AUD-010}, especificado
 * na Fatia 1 e sem publicador ate agora — hoje 3,8 milhoes de pagamentos por ciclo produzem
 * um unico registro de auditoria.
 *
 * <p>Carrega os fatores aplicados: depois de uma folha real, e possivel medir quanto cada
 * fator contribuiu para o total pago.
 */
public record PaymentGenerated(
        String paymentId,
        String cpf,
        String referencePeriod,
        BigDecimal grossAmount,
        BigDecimal netAmount,
        Map<FactorType, BigDecimal> appliedFactors,
        String cycleId,
        Actor actor,
        Instant occurredAt)
        implements AuditableEvent {

    public PaymentGenerated {
        appliedFactors = Map.copyOf(appliedFactors);
    }

    @Override
    public AuditAction action() {
        return AuditAction.INCLUSAO;
    }

    @Override
    public String entityType() {
        return "PAYMENT";
    }

    @Override
    public String entityId() {
        return paymentId;
    }

    @Override
    public Optional<String> subjectCpf() {
        return Optional.of(cpf);
    }

    @Override
    public Optional<String> batchRunId() {
        return Optional.of(cycleId);
    }

    @Override
    public Map<String, Change> changes() {
        Map<String, Change> changes = new LinkedHashMap<>();
        changes.put("grossAmount", Change.created(grossAmount.toPlainString()));
        changes.put("netAmount", Change.created(netAmount.toPlainString()));
        appliedFactors.forEach((type, value) ->
                changes.put("factor." + type.name(), Change.created(value.toPlainString())));
        return changes;
    }
}
