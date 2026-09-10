package br.gov.sifap.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-104 — cobre {@code REQ-AUD-003}.
 *
 * <p>O teste e concorrente de proposito. A tecnica legada le o maior numero uma vez por
 * execucao e incrementa em memoria ({@code CCAUDIT.NSC:64-71}): passa em teste sequencial
 * e colide com duas sessoes, sobre uma chave declarada unica.
 */
@Import(TestEventPublisher.Config.class)
class AuditNumberingIT extends AbstractAuditIT {

    private static final int THREADS = 8;
    private static final int EVENTS_PER_THREAD = 125;
    private static final int TOTAL = THREADS * EVENTS_PER_THREAD;

    @Autowired private TestEventPublisher publisher;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparTrilha() {
        jdbcTemplate.update("DELETE FROM audit_change_event");
    }

    @Test
    @DisplayName("deve atribuir numeros distintos quando sessoes concorrentes registram eventos")
    void deve_atribuir_numeros_distintos_em_concorrencia() throws InterruptedException {
        // AC-003.1 e AC-003.2
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger failures = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int thread = 0; thread < THREADS; thread++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < EVENTS_PER_THREAD; i++) {
                            publisher.publish(
                                    TestAuditEvent.beneficiaryRegistered("11144477735"));
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertThat(done.await(120, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(failures).hasValue(0);

        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_change_event", Integer.class);
        Integer distinctIds = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT id) FROM audit_change_event", Integer.class);

        assertThat(total).isEqualTo(TOTAL);
        assertThat(distinctIds).isEqualTo(TOTAL);
    }
}
