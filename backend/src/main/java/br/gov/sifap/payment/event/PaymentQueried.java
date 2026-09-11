package br.gov.sifap.payment.event;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import java.time.Instant;
import java.util.Optional;

/**
 * Consulta a pagamentos de um beneficiario.
 *
 * <p>Atende {@code REQ-AUD-004}. Valor recebido e dado pessoal; quem consultou fica
 * registrado na tabela de acesso, separada da de alteracao.
 */
public record PaymentQueried(String cpf, String purpose, Actor actor, Instant occurredAt)
        implements AuditableEvent {

    @Override
    public AuditAction action() {
        return AuditAction.CONSULTA;
    }

    @Override
    public String entityType() {
        return "PAYMENT";
    }

    @Override
    public String entityId() {
        return cpf;
    }

    @Override
    public Optional<String> subjectCpf() {
        return Optional.of(cpf);
    }
}
