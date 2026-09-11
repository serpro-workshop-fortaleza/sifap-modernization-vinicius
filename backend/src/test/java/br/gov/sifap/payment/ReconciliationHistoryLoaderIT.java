package br.gov.sifap.payment;

import static org.assertj.core.api.Assertions.assertThat;

import br.gov.sifap.payment.internal.PaymentPartitionMaintenance;
import br.gov.sifap.payment.internal.migration.ReconciliationHistoryLoader;
import br.gov.sifap.payment.internal.migration.ReconciliationMigrationReport;
import br.gov.sifap.support.AbstractIntegrationTest;
import br.gov.sifap.support.CpfGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-512 e T-513 — {@code REQ-REC-009}.
 *
 * <p>A carga nao recalcula e nao escolhe: ela mede quanto do historico jamais passou por
 * conferencia e reduz o conjunto ambiguo de duplicados ao que exige decisao humana.
 */
@DisplayName("Inventario da conciliacao historica")
class ReconciliationHistoryLoaderIT extends AbstractIntegrationTest {

    private static final String PERIOD = "202312";

    @Autowired
    private ReconciliationHistoryLoader loader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepare() {
        new PaymentPartitionMaintenance(jdbcTemplate, Clock.systemUTC()).ensurePartition(PERIOD);
        jdbcTemplate.update("DELETE FROM payment WHERE reference_period = ?", PERIOD);
    }

    /**
     * O container e compartilhado entre classes: a restricao precisa voltar, ou o teste de
     * unicidade do ciclo de folha passaria a verificar uma garantia que nao existe mais.
     */
    @AfterEach
    void restoreUniqueConstraint() {
        jdbcTemplate.update("DELETE FROM payment WHERE reference_period = ?", PERIOD);
        jdbcTemplate.execute("ALTER TABLE payment DROP CONSTRAINT IF EXISTS uq_payment_cpf_period");
        jdbcTemplate.execute(
                """
                ALTER TABLE payment ADD CONSTRAINT uq_payment_cpf_period
                    UNIQUE (cpf, reference_period)
                """);
    }

    /** Insere direto: a carga historica nao passa pelo agregado, por isso o teste tambem nao. */
    private void insertPayment(
            String cpf, String status, String reconciliationStatus, BigDecimal reconciledAmount) {
        jdbcTemplate.update(
                """
                INSERT INTO payment (cpf, program_code, reference_period, cycle_id,
                    amount_base, applied_factors, amount_gross, amount_net,
                    status, type, reconciliation_status, amount_reconciled,
                    credit_date, generated_at, generated_by)
                VALUES (?, '0001', ?, 'MIGRACAO', 1000.00, '{}'::jsonb, 1000.00, 1000.00,
                        ?, 'NORMAL', ?, ?, NULL, now(), 'MIGRACAO')
                """,
                cpf, PERIOD, status, reconciliationStatus, reconciledAmount);
    }

    @Test
    @DisplayName("deve contar o historico sem situacao de conciliacao quando inventariado")
    void deve_contar_o_historico_sem_situacao_de_conciliacao_quando_inventariado() {
        insertPayment(CpfGenerator.next(), "EMITIDO", null, null);
        insertPayment(CpfGenerator.next(), "CONFIRMADO", null, null);
        insertPayment(CpfGenerator.next(), "CONFIRMADO", "CONCILIADO", new BigDecimal("1000.00"));

        ReconciliationMigrationReport report = loader.inventory(PERIOD);

        assertThat(report.paymentsRead()).isEqualTo(3);
        assertThat(report.neverReconciled()).isEqualTo(2);
        assertThat(report.reconciled()).isEqualTo(1);
        assertThat(report.hasUnreconciledHistory()).isTrue();
    }

    @Test
    @DisplayName("deve separar o divergente do conciliado quando ambos existem no periodo")
    void deve_separar_o_divergente_do_conciliado_quando_ambos_existem_no_periodo() {
        insertPayment(CpfGenerator.next(), "CONFIRMADO", "CONCILIADO", new BigDecimal("1000.00"));
        insertPayment(CpfGenerator.next(), "EMITIDO", "DIVERGENTE", new BigDecimal("900.00"));

        ReconciliationMigrationReport report = loader.inventory(PERIOD);

        assertThat(report.reconciled()).isEqualTo(1);
        assertThat(report.divergent()).isEqualTo(1);
        assertThat(loader.confirmedTotal(PERIOD)).contains(new BigDecimal("1000.00"));
    }

    /**
     * {@code SIFAP-M-06} pela via da conciliacao: a Fatia 4 contou os duplicados e nao
     * escolheu. O credito bancario resolve os que tem um unico correspondente.
     */
    @Test
    @DisplayName("deve resolver o duplicado quando apenas um dos registros tem credito bancario")
    void deve_resolver_o_duplicado_quando_apenas_um_dos_registros_tem_credito_bancario() {
        String duplicated = CpfGenerator.next();
        insertDuplicate(duplicated, "CONCILIADO", new BigDecimal("1000.00"));
        insertDuplicate(duplicated, null, null);

        ReconciliationMigrationReport report = loader.inventory(PERIOD);

        assertThat(report.divergentResolved()).isEqualTo(1);
        assertThat(report.stillAmbiguous()).isZero();
    }

    @Test
    @DisplayName("deve manter o duplicado ambiguo quando nenhum registro tem credito")
    void deve_manter_o_duplicado_ambiguo_quando_nenhum_registro_tem_credito() {
        String duplicated = CpfGenerator.next();
        insertDuplicate(duplicated, null, null);
        insertDuplicate(duplicated, null, null);

        ReconciliationMigrationReport report = loader.inventory(PERIOD);

        assertThat(report.divergentResolved()).isZero();
        assertThat(report.stillAmbiguous()).isEqualTo(1);
    }

    /**
     * O duplicado so existe no historico migrado; {@code uq_payment_cpf_period} o impede
     * daqui para frente. A insercao desabilita a restricao para reproduzir a base real.
     */
    private void insertDuplicate(String cpf, String reconciliationStatus, BigDecimal amount) {
        jdbcTemplate.execute("ALTER TABLE payment DROP CONSTRAINT IF EXISTS uq_payment_cpf_period");
        insertPayment(cpf, "EMITIDO", reconciliationStatus, amount);
    }
}
