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
 * Correcao retroativa aplicada.
 *
 * <p>Diferente dos descontos, o legado <strong>registra</strong> esta operacao:
 * {@code CALCCORR.NSP:243} inclui o copycode de auditoria. O que ele nao registra e o
 * indice aplicado nem o periodo coberto, o que o {@code REQ-PAY-021} corrige.
 */
public record PaymentCorrected(
        String paymentId,
        String cpf,
        BigDecimal originalAmount,
        BigDecimal correctedAmount,
        BigDecimal appliedIndex,
        String coveredPeriod,
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
                "amountCorrection",
                new Change(originalAmount.toPlainString(), correctedAmount.toPlainString()),
                "correctionIndex",
                Change.created(appliedIndex.toPlainString()),
                "correctionPeriod",
                Change.created(coveredPeriod));
    }
}
