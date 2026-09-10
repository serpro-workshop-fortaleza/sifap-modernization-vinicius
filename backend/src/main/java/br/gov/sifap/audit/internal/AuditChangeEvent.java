package br.gov.sifap.audit.internal;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.ActorType;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.Change;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Evento de inclusao, alteracao, exclusao, conciliacao ou processamento.
 *
 * <p>Cobre {@code REQ-AUD-003} a {@code REQ-AUD-006} e {@code REQ-AUD-009}.
 * Nao ha setter: o registro e imutavel tambem em memoria, e nao apenas no banco.
 */
@Entity
@Table(name = "audit_change_event")
public class AuditChangeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auditChangeEventSeq")
    @SequenceGenerator(
            name = "auditChangeEventSeq",
            sequenceName = "audit_change_event_id_seq",
            allocationSize = 500)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20, updatable = false)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, length = 40, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 50, updatable = false)
    private String entityId;

    @Column(name = "subject_cpf", length = 11, updatable = false)
    private String subjectCpf;

    @Column(name = "actor_id", nullable = false, length = 50, updatable = false)
    private String actorId;

    @Column(name = "actor_profile", nullable = false, length = 30, updatable = false)
    private String actorProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20, updatable = false)
    private ActorType actorType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", updatable = false)
    private Map<String, Change> changes;

    @Column(name = "batch_run_id", length = 50, updatable = false)
    private String batchRunId;

    protected AuditChangeEvent() {
        // exigido pelo JPA
    }

    static AuditChangeEvent from(AuditableEvent event) {
        if (event.action().isAccessToPersonalData()) {
            throw new IllegalArgumentException(
                    "acao de acesso pertence a audit_access_event: " + event.action());
        }

        AuditChangeEvent entity = new AuditChangeEvent();
        entity.occurredAt = event.occurredAt();
        entity.action = event.action();
        entity.entityType = event.entityType();
        entity.entityId = event.entityId();
        entity.subjectCpf = event.subjectCpf().orElse(null);

        Actor actor = event.actor();
        entity.actorId = actor.id();
        entity.actorProfile = actor.profile();
        entity.actorType = actor.type();

        entity.changes = event.changes().isEmpty() ? null : Map.copyOf(event.changes());
        entity.batchRunId = event.batchRunId().orElse(null);
        return entity;
    }

    public Long id() {
        return id;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public AuditAction action() {
        return action;
    }

    public String entityType() {
        return entityType;
    }

    public String entityId() {
        return entityId;
    }

    public Optional<String> subjectCpf() {
        return Optional.ofNullable(subjectCpf);
    }

    public Actor actor() {
        return new Actor(actorId, actorProfile, actorType);
    }

    public Map<String, Change> changes() {
        return changes == null ? Map.of() : Map.copyOf(changes);
    }

    public Optional<String> batchRunId() {
        return Optional.ofNullable(batchRunId);
    }
}
