package br.gov.sifap.audit.internal;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.ActorType;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
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
import java.util.Optional;

/**
 * Evento de consulta a dado pessoal.
 *
 * <p>Cobre {@code REQ-AUD-007}. Fica em tabela propria porque consulta e conciliacao
 * respondem por 89% do volume legado, e mante-las junto dos eventos de negocio
 * significa varrer 311 GB para encontrar 84 mil registros.
 *
 * <p>A separacao tambem resolve o conflito normativo: registra o acesso, atendendo a
 * {@code IN-TCU 63/2010}, com retencao propria que limita o crescimento, atendendo a
 * preocupacao da {@code PORT. CGTI 213/2010}.
 */
@Entity
@Table(name = "audit_access_event")
public class AuditAccessEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auditAccessEventSeq")
    @SequenceGenerator(
            name = "auditAccessEventSeq",
            sequenceName = "audit_access_event_id_seq",
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

    protected AuditAccessEvent() {
        // exigido pelo JPA
    }

    static AuditAccessEvent from(AuditableEvent event) {
        if (!event.action().isAccessToPersonalData()) {
            throw new IllegalArgumentException(
                    "acao de alteracao pertence a audit_change_event: " + event.action());
        }

        AuditAccessEvent entity = new AuditAccessEvent();
        entity.occurredAt = event.occurredAt();
        entity.action = event.action();
        entity.entityType = event.entityType();
        entity.entityId = event.entityId();
        entity.subjectCpf = event.subjectCpf().orElse(null);

        Actor actor = event.actor();
        entity.actorId = actor.id();
        entity.actorProfile = actor.profile();
        entity.actorType = actor.type();
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
}
