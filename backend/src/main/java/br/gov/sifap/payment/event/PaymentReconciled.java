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
 * Pagamento conciliado com o retorno do banco.
 *
 * <p>Origem em {@code BATCHCON.NSP:316-329}, que ja audita cada conciliacao. O que muda e
 * a acao: o legado grava {@code CO}, o mesmo codigo que {@code CONSBENF.NSP:172} usa para
 * consulta ({@code REQ-REC-013}).
 */
public record PaymentReconciled(
        String paymentId,
        String cpf,
        String previousStatus,
        String newStatus,
        BigDecimal bankAmount,
        String returnCode,
        String runId,
        Actor actor,
        Instant occurredAt)
        implements AuditableEvent {

    @Override
    public AuditAction action() {
        return AuditAction.CONCILIACAO;
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
        return Optional.of(runId);
    }

    @Override
    public Map<String, Change> changes() {
        return Map.of(
                "status", new Change(previousStatus, newStatus),
                "amountReconciled", Change.created(bankAmount.toPlainString()),
                "bankReturnCode", Change.created(returnCode));
    }
}
