package br.gov.sifap.audit;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.Change;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Projecao de leitura de um evento da trilha.
 *
 * <p>Existe para que o consumidor nao alcance a entidade JPA do contexto de auditoria.
 *
 * @param changes vazio quando o evento nao e de alteracao
 */
public record AuditEventView(
        long id,
        Instant occurredAt,
        AuditAction action,
        String entityType,
        String entityId,
        Optional<String> subjectCpf,
        Actor actor,
        Map<String, Change> changes,
        Optional<String> batchRunId) {
}
