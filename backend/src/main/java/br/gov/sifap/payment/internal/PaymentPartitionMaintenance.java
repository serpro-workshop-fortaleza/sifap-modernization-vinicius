package br.gov.sifap.payment.internal;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Manutencao das particoes da folha.
 *
 * <p>Como na trilha de auditoria, <strong>nao</strong> e um bean da aplicacao: criar
 * particao e DDL, e a role da aplicacao nao tem esse direito. O agendamento pertence ao
 * job de operacao, com credencial propria.
 *
 * <p>180 milhoes de registros crescendo 3,8 milhoes por mes. O legado mantem tudo em um
 * unico arquivo Adabas e, segundo {@code BATCHPGT.NSP:39}, a janela ja passa de quatro
 * horas.
 */
public class PaymentPartitionMaintenance {

    static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyyMM");

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public PaymentPartitionMaintenance(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public String ensurePartition(String referencePeriod) {
        return jdbcTemplate.queryForObject(
                "SELECT payment_create_partition(?)", String.class, referencePeriod);
    }

    public void ensurePartitionsAhead(int months) {
        YearMonth current = YearMonth.now(clock);
        for (int offset = 0; offset <= months; offset++) {
            ensurePartition(current.plusMonths(offset).format(PERIOD));
        }
    }
}
