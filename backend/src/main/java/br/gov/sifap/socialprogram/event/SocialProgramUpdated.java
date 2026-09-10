package br.gov.sifap.socialprogram.event;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.Change;
import java.time.Instant;
import java.util.Map;

/**
 * Parametros do programa alterados.
 *
 * <p>Evento que o legado nao tem. O {@code CADPROG} implementa apenas inclusao e consulta
 * ({@code CADPROG.NSP:80-83}), de modo que um reajuste anual nao tem caminho no sistema.
 */
public record SocialProgramUpdated(
        String programCode, Map<String, Change> changes, Actor actor, Instant occurredAt)
        implements AuditableEvent {

    public SocialProgramUpdated {
        changes = Map.copyOf(changes);
    }

    @Override
    public AuditAction action() {
        return AuditAction.ALTERACAO;
    }

    @Override
    public String entityType() {
        return "SOCIAL_PROGRAM";
    }

    @Override
    public String entityId() {
        return programCode;
    }
}
