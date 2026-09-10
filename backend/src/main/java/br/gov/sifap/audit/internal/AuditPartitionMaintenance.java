package br.gov.sifap.audit.internal;

import br.gov.sifap.audit.AuditRetentionPolicy;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Manutencao das particoes da trilha: criacao das futuras e expurgo das vencidas.
 *
 * <p>Cobre {@code REQ-AUD-013} e apoia {@code REQ-AUD-007}.
 *
 * <p>Nao e um bean da aplicacao. As duas operacoes sao manutencao de schema e exigem a
 * role {@code sifap_audit_purge}, que a aplicacao nao possui — a role da aplicacao tem
 * apenas {@code SELECT} e {@code INSERT}, que e o que garante o {@code REQ-AUD-002}.
 * O agendamento pertence ao job de operacao, com credencial propria.
 *
 * <p>O expurgo remove particoes inteiras. Sobre uma trilha de 311 GB, {@code DELETE}
 * em centenas de milhoes de linhas nao termina dentro de nenhuma janela util.
 */
public class AuditPartitionMaintenance {

    private final JdbcTemplate jdbcTemplate;
    private final AuditRetentionPolicy policy;
    private final Clock clock;

    public AuditPartitionMaintenance(
            JdbcTemplate jdbcTemplate, AuditRetentionPolicy policy, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.policy = policy;
        this.clock = clock;
    }

    public String ensurePartition(AuditTable table, YearMonth month) {
        return jdbcTemplate.queryForObject(
                "SELECT audit_create_partition(?, ?)",
                String.class,
                table.tableName(),
                Date.valueOf(month.atDay(1)));
    }

    public void ensurePartitionsAhead(int months) {
        YearMonth current = YearMonth.now(clock);
        for (AuditTable table : AuditTable.values()) {
            for (int offset = 0; offset <= months; offset++) {
                ensurePartition(table, current.plusMonths(offset));
            }
        }
    }

    /** @return quantidade de particoes removidas */
    public int purgeExpiredPartitions() {
        LocalDate today = LocalDate.now(clock);
        return dropBefore(AuditTable.CHANGE, today.minus(policy.changeRetention()))
                + dropBefore(AuditTable.ACCESS, today.minus(policy.accessRetention()));
    }

    private int dropBefore(AuditTable table, LocalDate cutoff) {
        Integer dropped = jdbcTemplate.queryForObject(
                "SELECT audit_drop_partitions_before(?, ?)",
                Integer.class,
                table.tableName(),
                Date.valueOf(cutoff));
        return dropped == null ? 0 : dropped;
    }
}
