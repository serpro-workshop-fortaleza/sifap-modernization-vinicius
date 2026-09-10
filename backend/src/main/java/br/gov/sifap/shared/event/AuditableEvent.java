package br.gov.sifap.shared.event;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Fato de negocio consumado que a trilha de auditoria registra.
 *
 * <p>No legado, cada programa monta o registro e chama {@code PERFORM WRITE-AUDIT}.
 * O acoplamento direto produziu a lacuna das regras 80 e 95: {@code CALCBENF} e
 * {@code CALCDSCT} alteram valores financeiros e nao incluem o copycode.
 *
 * <p>Com este contrato, o contexto de origem apenas publica o fato. Garantir o registro
 * passa a ser responsabilidade da auditoria, e nao da lembranca de cada autor.
 *
 * <p>Implementacoes sao {@code record} imutaveis e nunca carregam entidade JPA no
 * payload, para que o consumidor nao navegue pelo modelo do publicador.
 * O catalogo completo esta em {@code 02-modern-spec/domain-events.md}.
 */
public interface AuditableEvent {

    AuditAction action();

    String entityType();

    String entityId();

    Actor actor();

    Instant occurredAt();

    /** CPF do titular afetado, quando o evento envolve dado pessoal. */
    default Optional<String> subjectCpf() {
        return Optional.empty();
    }

    /** Campos alterados; vazio em inclusao, exclusao, consulta e processamento. */
    default Map<String, Change> changes() {
        return Map.of();
    }

    /** Identificador da execucao, quando a origem e processamento automatico. */
    default Optional<String> batchRunId() {
        return Optional.empty();
    }
}
