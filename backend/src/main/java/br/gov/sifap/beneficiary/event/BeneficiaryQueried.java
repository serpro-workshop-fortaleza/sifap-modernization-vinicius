package br.gov.sifap.beneficiary.event;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import java.time.Instant;
import java.util.Optional;

/**
 * Dado pessoal consultado.
 *
 * <p>Origem no legado: {@code CONSBENF.NSP:168-179}. Unico evento deste contexto roteado
 * para a trilha de acesso, com retencao propria ({@code REQ-AUD-007}).
 */
public record BeneficiaryQueried(String cpf, String purpose, Actor actor, Instant occurredAt)
        implements AuditableEvent {

    @Override
    public AuditAction action() {
        return AuditAction.CONSULTA;
    }

    @Override
    public String entityType() {
        return "BENEFICIARY";
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
