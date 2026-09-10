package br.gov.sifap.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.audit.internal.AuditPartitionMaintenance;
import br.gov.sifap.audit.internal.AuditTable;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * T-112 — cobre {@code REQ-AUD-013} e apoia {@code REQ-AUD-007}.
 *
 * <p>O expurgo remove particoes inteiras e roda sob a role restrita, nao sob a role da
 * aplicacao. Se rodasse sob a role da aplicacao, a imutabilidade do {@code REQ-AUD-002}
 * seria apenas aparente.
 */
class AuditRetentionIT extends AbstractAuditIT {

    private static final String PURGE_USER = "purge_tester";
    private static final String PURGE_PASSWORD = randomPassword();

    @Autowired private JdbcTemplate ownerJdbcTemplate;

    private AuditPartitionMaintenance maintenance;
    private LocalDate today;

    @BeforeEach
    void prepararRoleDeExpurgo() {
        ownerJdbcTemplate.execute(
                "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '"
                        + PURGE_USER
                        + "') THEN CREATE USER "
                        + PURGE_USER
                        + " PASSWORD '"
                        + PURGE_PASSWORD
                        + "'; END IF; END $$");
        ownerJdbcTemplate.execute("GRANT sifap_audit_purge TO " + PURGE_USER);

        today = LocalDate.now();
        maintenance = new AuditPartitionMaintenance(
                new JdbcTemplate(purgeDataSource()),
                new AuditRetentionPolicy(Period.ofYears(10), Period.ofDays(90)),
                Clock.systemUTC());
    }

    @Test
    @DisplayName("deve preservar a particao de alteracao quando tem menos de dez anos")
    void deve_preservar_a_particao_de_alteracao_com_menos_de_dez_anos() {
        // AC-013.1
        YearMonth recent = YearMonth.from(today.minusYears(3));
        maintenance.ensurePartition(AuditTable.CHANGE, recent);

        maintenance.purgeExpiredPartitions();

        assertThat(partitionsOf("audit_change_event")).contains(partitionName("audit_change_event", recent));
    }

    @Test
    @DisplayName("deve remover a particao de alteracao quando ultrapassa o prazo legal")
    void deve_remover_a_particao_de_alteracao_vencida() {
        YearMonth expired = YearMonth.from(today.minusYears(11));
        maintenance.ensurePartition(AuditTable.CHANGE, expired);

        maintenance.purgeExpiredPartitions();

        assertThat(partitionsOf("audit_change_event"))
                .doesNotContain(partitionName("audit_change_event", expired));
    }

    @Test
    @DisplayName("deve expurgar acesso pelo prazo proprio sem afetar os eventos de alteracao")
    void deve_expurgar_acesso_sem_afetar_alteracao() {
        // AC-007.2: retencao de acesso e independente da retencao legal de dez anos.
        YearMonth expiredAccess = YearMonth.from(today.minusMonths(8));
        YearMonth changeSamePeriod = YearMonth.from(today.minusMonths(8));
        maintenance.ensurePartition(AuditTable.ACCESS, expiredAccess);
        maintenance.ensurePartition(AuditTable.CHANGE, changeSamePeriod);

        maintenance.purgeExpiredPartitions();

        assertThat(partitionsOf("audit_access_event"))
                .doesNotContain(partitionName("audit_access_event", expiredAccess));
        assertThat(partitionsOf("audit_change_event"))
                .contains(partitionName("audit_change_event", changeSamePeriod));
    }

    @Test
    @DisplayName("deve recusar a politica quando a retencao de alteracao e inferior a dez anos")
    void deve_recusar_politica_abaixo_do_minimo_legal() {
        // REQ-AUD-013 vem da Lei 8159, art. 14. Uma configuracao invalida impede a
        // aplicacao de subir, em vez de expurgar dado que deveria ser preservado.
        assertThatThrownBy(() ->
                        new AuditRetentionPolicy(Period.ofYears(9), Period.ofDays(90)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dez anos");
    }

    @Test
    @DisplayName("deve recusar a operacao quando a funcao de expurgo recebe tabela fora da trilha")
    void deve_recusar_tabela_fora_da_trilha() {
        // A funcao e SECURITY DEFINER: sem esta guarda, seria um vetor de exclusao arbitraria.
        JdbcTemplate purge = new JdbcTemplate(purgeDataSource());

        assertThatThrownBy(() -> purge.queryForObject(
                        "SELECT audit_drop_partitions_before(?, ?)",
                        Integer.class,
                        "flyway_schema_history",
                        java.sql.Date.valueOf(today)))
                .hasMessageContaining("fora da trilha de auditoria");
    }

    @Test
    @DisplayName("deve criar particoes futuras quando a manutencao roda com antecedencia")
    void deve_criar_particoes_futuras() {
        maintenance.ensurePartitionsAhead(6);

        YearMonth sixMonthsAhead = YearMonth.now().plusMonths(6);

        assertThat(partitionsOf("audit_change_event"))
                .contains(partitionName("audit_change_event", sixMonthsAhead));
        assertThat(partitionsOf("audit_access_event"))
                .contains(partitionName("audit_access_event", sixMonthsAhead));
    }

    private List<String> partitionsOf(String parent) {
        return ownerJdbcTemplate.queryForList(
                """
                SELECT child.relname
                FROM pg_inherits i
                JOIN pg_class child ON child.oid = i.inhrelid
                JOIN pg_class p ON p.oid = i.inhparent
                WHERE p.relname = ?
                """,
                String.class,
                parent);
    }

    private static String partitionName(String table, YearMonth month) {
        return "%s_%d%02d".formatted(table, month.getYear(), month.getMonthValue());
    }

    private DataSource purgeDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(PURGE_USER);
        dataSource.setPassword(PURGE_PASSWORD);
        return dataSource;
    }

    private static String randomPassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
