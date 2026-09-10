package br.gov.sifap.beneficiary.event;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import java.time.Instant;
import java.util.Optional;

/**
 * Beneficiario incluido.
 *
 * <p>Origem no legado: {@code CADBENEF.NSP:295}.
 */
public record BeneficiaryRegistered(String cpf, String programCode, Actor actor, Instant occurredAt)
        implements AuditableEvent {

    @Override
    public AuditAction action() {
        return AuditAction.INCLUSAO;
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
