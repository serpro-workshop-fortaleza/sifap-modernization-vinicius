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
 * Divergencia entre o valor pago e o valor confirmado pelo banco.
 *
 * <p>Generaliza {@code BATCHCON.NSP:331-345}, que e o <strong>unico</strong> ponto de todo
 * o acervo a preencher {@code AMT-PREV} e {@code AMT-NEW} — os campos de valor anterior e
 * posterior que o dicionario declara desde 1997 e que a Fatia 1 encontrou vazios em toda
 * parte.
 *
 * <p>A diferenca e que agora a divergencia tambem fica no proprio pagamento
 * ({@code REQ-REC-009}); a trilha deixa de ser a unica fonte.
 */
public record PaymentDivergenceDetected(
        String paymentId,
        String cpf,
        BigDecimal systemAmount,
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
                "amountNet", new Change(systemAmount.toPlainString(), bankAmount.toPlainString()),
                "reconciliationStatus", Change.created("DIVERGENTE"),
                "bankReturnCode", Change.created(returnCode));
    }
}
