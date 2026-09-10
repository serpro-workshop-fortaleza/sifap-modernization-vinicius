package br.gov.sifap.socialprogram.event;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.Change;
import br.gov.sifap.socialprogram.SocialProgramStatus;
import java.time.Instant;
import java.util.Map;

/**
 * Situacao do programa alterada.
 *
 * <p>Evento que o legado nao tem. {@code SOCPROG.ddm:37} prev\u00ea {@code INATIVO} e
 * {@code ENCERRADO} desde 1997, e {@code CADPROG.NSP:134} grava {@code A} sempre — de modo
 * que a recusa por programa inativo de {@code VALELEG.NSN:114-118} nunca e acionada.
 */
public record SocialProgramStatusChanged(
        String programCode,
        SocialProgramStatus previousStatus,
        SocialProgramStatus newStatus,
        String reason,
        Actor actor,
        Instant occurredAt)
        implements AuditableEvent {

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

    @Override
    public Map<String, Change> changes() {
        return Map.of(
                "status", new Change(previousStatus.name(), newStatus.name()),
                "statusReason", Change.created(reason));
    }
}
