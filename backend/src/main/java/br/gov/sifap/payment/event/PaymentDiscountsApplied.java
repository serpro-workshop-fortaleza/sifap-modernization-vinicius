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
 * Descontos aplicados a um pagamento.
 *
 * <p>Evento que o legado nao tem. O {@code CALCDSCT} altera valores financeiros e nao possui
 * {@code INCLUDE CCAUDIT}: e a lacuna da regra 95, contrariando a {@code IN-TCU 63/2010}
 * citada no proprio cabecalho do copycode de auditoria.
 */
public record PaymentDiscountsApplied(
        String paymentId,
        String cpf,
        BigDecimal previousDiscount,
        BigDecimal newDiscount,
        BigDecimal netAmount,
        Actor actor,
        Instant occurredAt)
        implements AuditableEvent {

    @Override
    public AuditAction action() {
        return AuditAction.ALTERACAO;
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
    public Map<String, Change> changes() {
        return Map.of(
                "amountDiscount",
                new Change(previousDiscount.toPlainString(), newDiscount.toPlainString()),
                "amountNet",
                Change.created(netAmount.toPlainString()));
    }
}
