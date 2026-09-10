package br.gov.sifap.beneficiary.event;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.Change;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Dependente incluido.
 *
 * <p>Origem no legado: {@code CADDEPEN.NSP:205-211}, que registra a inclusao como
 * alteracao do titular. O dependente nao existe como sujeito proprio na trilha, e este
 * evento preserva esse enquadramento.
 */
public record DependentAdded(
        String holderCpf,
        String dependentName,
        String relation,
        int activeDependentCount,
        Actor actor,
        Instant occurredAt)
        implements AuditableEvent {

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
        return holderCpf;
    }

    @Override
    public Optional<String> subjectCpf() {
        return Optional.of(holderCpf);
    }

    @Override
    public Map<String, Change> changes() {
        return Map.of(
                "dependentAdded", Change.created(dependentName),
                "relation", Change.created(relation),
                "activeDependentCount", Change.created(String.valueOf(activeDependentCount)));
    }
}
