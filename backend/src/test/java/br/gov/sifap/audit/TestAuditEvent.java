package br.gov.sifap.audit;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.Change;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Eventos de teste que representam os publicadores das fatias 2 a 5.
 *
 * <p>Os contextos de origem ainda nao existem. O que a Fatia 1 entrega e o consumidor e
 * o contrato; estes registros exercitam a forma do evento definida em
 * {@code 02-modern-spec/domain-events.md}.
 */
record TestAuditEvent(
        AuditAction action,
        String entityType,
        String entityId,
        Optional<String> subjectCpf,
        Actor actor,
        Instant occurredAt,
        Map<String, Change> changes,
        Optional<String> batchRunId)
        implements AuditableEvent {

    static final Actor OPERATOR = Actor.human("op.silva", "SUPERVISOR");
    static final Actor BATCH = Actor.process("BATCHPGT");

    /** Equivale a {@code BeneficiaryRegistered}. */
    static TestAuditEvent beneficiaryRegistered(String cpf) {
        return new TestAuditEvent(
                AuditAction.INCLUSAO,
                "BENEFICIARY",
                cpf,
                Optional.of(cpf),
                OPERATOR,
                Instant.now(),
                Map.of(),
                Optional.empty());
    }

    /** Equivale a {@code BeneficiaryUpdated}. */
    static TestAuditEvent beneficiaryUpdated(String cpf, Map<String, Change> changes) {
        return new TestAuditEvent(
                AuditAction.ALTERACAO,
                "BENEFICIARY",
                cpf,
                Optional.of(cpf),
                OPERATOR,
                Instant.now(),
                changes,
                Optional.empty());
    }

    /** Equivale a {@code BeneficiaryQueried}, unico evento roteado para a trilha de acesso. */
    static TestAuditEvent beneficiaryQueried(String cpf) {
        return new TestAuditEvent(
                AuditAction.CONSULTA,
                "BENEFICIARY",
                cpf,
                Optional.of(cpf),
                OPERATOR,
                Instant.now(),
                Map.of(),
                Optional.empty());
    }

    /** Equivale a {@code PaymentGenerated}. */
    static TestAuditEvent paymentGenerated(String paymentId, String cpf, String batchRunId) {
        return new TestAuditEvent(
                AuditAction.INCLUSAO,
                "PAYMENT",
                paymentId,
                Optional.of(cpf),
                BATCH,
                Instant.now(),
                Map.of(),
                Optional.of(batchRunId));
    }

    /** Equivale a {@code PayrollCycleCompleted}. */
    static TestAuditEvent payrollCycleCompleted(String batchRunId, int generated) {
        return new TestAuditEvent(
                AuditAction.PROCESSAMENTO,
                "PAYROLL_CYCLE",
                batchRunId,
                Optional.empty(),
                BATCH,
                Instant.now(),
                Map.of("generated", Change.created(String.valueOf(generated))),
                Optional.of(batchRunId));
    }

    TestAuditEvent at(Instant instant) {
        return new TestAuditEvent(
                action, entityType, entityId, subjectCpf, actor, instant, changes, batchRunId);
    }
}
