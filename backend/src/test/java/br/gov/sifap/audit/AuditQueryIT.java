package br.gov.sifap.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-111 — cobre {@code REQ-AUD-012}.
 *
 * <p>{@code AUDIT.ddm:102} manda usar o superdescritor de entidade e data em consulta
 * pesada. O equivalente aqui e o indice composto, e a verificacao e o plano de execucao:
 * sobre 311 GB, varredura sequencial nao e uma opcao degradada — e uma consulta que
 * nunca termina.
 */
class AuditQueryIT extends AbstractAuditIT {

    private static final int VOLUME = 20_000;
    private static final int DISTINCT_ENTITIES = 5_000;

    @Autowired private AuditQuery auditQuery;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void popularTrilha() {
        jdbcTemplate.update("DELETE FROM audit_change_event");
        jdbcTemplate.update(
                """
                INSERT INTO audit_change_event
                    (occurred_at, action, entity_type, entity_id, subject_cpf,
                     actor_id, actor_profile, actor_type)
                SELECT now() - (g || ' minutes')::interval,
                       'ALTERACAO',
                       'BENEFICIARY',
                       'ENT-' || (g %% %d),
                       lpad((g %% %d)::text, 11, '0'),
                       'op.silva', 'SUPERVISOR', 'HUMANO'
                FROM generate_series(1, %d) g
                """
                        .formatted(DISTINCT_ENTITIES, DISTINCT_ENTITIES, VOLUME));
        jdbcTemplate.execute("ANALYZE audit_change_event");
    }

    @Test
    @DisplayName("deve retornar os eventos da entidade quando a consulta informa periodo")
    void deve_retornar_os_eventos_da_entidade_no_periodo() {
        // AC-012.1
        List<AuditEventView> events = auditQuery.findChangesByEntity(
                "BENEFICIARY",
                "ENT-7",
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS));

        assertThat(events)
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.entityId()).isEqualTo("ENT-7"))
                .isSortedAccordingTo((a, b) -> b.occurredAt().compareTo(a.occurredAt()));
    }

    @Test
    @DisplayName("deve usar indice quando a consulta filtra por entidade e periodo")
    void deve_usar_indice_quando_a_consulta_filtra_por_entidade_e_periodo() {
        // AC-012.2
        String plan = explain(
                """
                SELECT * FROM audit_change_event
                WHERE entity_type = 'BENEFICIARY' AND entity_id = 'ENT-7'
                  AND occurred_at >= now() - interval '30 days'
                  AND occurred_at < now() + interval '1 day'
                ORDER BY occurred_at DESC
                """);

        assertThat(plan).contains("Index").doesNotContain("Seq Scan");
    }

    @Test
    @DisplayName("deve usar indice quando a consulta filtra por CPF do titular")
    void deve_usar_indice_quando_a_consulta_filtra_por_cpf() {
        String plan = explain(
                """
                SELECT * FROM audit_change_event
                WHERE subject_cpf = '00000000007'
                  AND occurred_at >= now() - interval '30 days'
                  AND occurred_at < now() + interval '1 day'
                ORDER BY occurred_at DESC
                """);

        assertThat(plan).contains("Index").doesNotContain("Seq Scan");
    }

    private String explain(String sql) {
        return String.join(
                "\n", jdbcTemplate.queryForList("EXPLAIN " + sql, String.class));
    }
}
