package br.gov.sifap.beneficiary.event;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.Change;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Dados cadastrais alterados.
 *
 * <p>Origem no legado: {@code CADBENEF.NSP:318}. O par anterior/posterior atende o
 * {@code REQ-AUD-006}: a estrutura existe em {@code AUDIT.ddm:61-69} desde 2005 e nunca
 * foi preenchida.
 */
public record BeneficiaryUpdated(
        String cpf, Map<String, Change> changes, Actor actor, Instant occurredAt)
        implements AuditableEvent {

    public BeneficiaryUpdated {
        changes = Map.copyOf(changes);
    }

    @Override
    public AuditAction action() {
        return AuditAction.ALTERACAO;
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
