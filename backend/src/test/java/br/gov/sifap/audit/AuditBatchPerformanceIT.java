package br.gov.sifap.audit;

import static org.assertj.core.api.Assertions.assertThat;

import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.AuditableEventBatch;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-110 — apoia {@code REQ-AUD-010}.
 *
 * <p>Risco registrado no plano: o listener roda dentro da transacao de negocio, e a
 * Fatia 4 gera 3,8 milhoes de eventos por ciclo numa janela de 4 horas — cerca de
 * 264 eventos por segundo apenas para nao estourar a janela.
 *
 * <p>Medir aqui evita descobrir o problema na folha, quando o custo de mudar o desenho
 * ja e alto.
 */
@Import(TestEventPublisher.Config.class)
class AuditBatchPerformanceIT extends AbstractAuditIT {

    private static final int TOTAL_EVENTS = 100_000;
    private static final int BATCH_SIZE = 5_000;

    /** Folga generosa sobre os 264 eventos por segundo que a folha mensal exige. */
    private static final Duration BUDGET = Duration.ofSeconds(120);

    @Autowired private TestEventPublisher publisher;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparTrilha() {
        jdbcTemplate.update("DELETE FROM audit_change_event");
    }

    @Test
    @DisplayName("deve gravar cem mil eventos dentro da janela quando o lote e usado")
    void deve_gravar_cem_mil_eventos_dentro_da_janela() {
        long startedAt = System.nanoTime();

        for (int offset = 0; offset < TOTAL_EVENTS; offset += BATCH_SIZE) {
            publisher.publish(new AuditableEventBatch(batchOf(offset)));
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        long perSecond = TOTAL_EVENTS * 1000L / Math.max(1, elapsed.toMillis());

        Integer recorded = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_change_event", Integer.class);

        assertThat(recorded).isEqualTo(TOTAL_EVENTS);
        assertThat(elapsed)
                .as("%d eventos em %s (%d/s); a folha mensal exige ao menos 264/s",
                        TOTAL_EVENTS, elapsed, perSecond)
                .isLessThan(BUDGET);
    }

    private static List<AuditableEvent> batchOf(int offset) {
        List<AuditableEvent> events = new ArrayList<>(BATCH_SIZE);
        for (int i = 0; i < BATCH_SIZE; i++) {
            events.add(TestAuditEvent.paymentGenerated(
                    "PGTO-" + (offset + i), "11144477735", "CICLO-CARGA"));
        }
        return events;
    }
}
