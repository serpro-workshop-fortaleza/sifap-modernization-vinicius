package br.gov.sifap.payment;

import static org.assertj.core.api.Assertions.assertThat;

import br.gov.sifap.payment.internal.migration.PaymentLoader;
import br.gov.sifap.payment.internal.migration.PaymentMigrationReport;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.support.AbstractIntegrationTest;
import br.gov.sifap.support.CpfGenerator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-415 — {@code REQ-PAY-016}.
 *
 * <p>A carga nao corrige o passado; ela o torna contavel.
 */
@DisplayName("Carga do historico de pagamentos")
class PaymentLoaderIT extends AbstractIntegrationTest {

    private static final Actor MIGRATION = Actor.process("MIGRACAO-FOLHA");
    private static final String PERIOD = "202401";

    @Autowired
    private PaymentLoader loader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void ensurePartition() {
        jdbcTemplate.queryForObject("SELECT payment_create_partition(?)", String.class, PERIOD);
    }

    private static String line(String number, String cpf, String gross, String discount, String net, String type) {
        return String.join(";", number, cpf, "0001", PERIOD, gross, discount, net, "C", type);
    }

    @Test
    @DisplayName("deve migrar o historico sem recalcular quando os registros sao lidos")
    void deve_migrar_o_historico_sem_recalcular_quando_os_registros_sao_lidos() {
        ensurePartition();
        String cpf = CpfGenerator.next();

        PaymentMigrationReport report =
                loader.load(List.of(line("1001", cpf, "100000", "10000", "90000", "N")), MIGRATION);

        assertThat(report.paymentsRead()).isEqualTo(1);
        assertThat(report.paymentsMigrated()).isEqualTo(1);
        assertThat(report.noPaymentDiscarded()).isTrue();
    }

    /** {@code SIFAP-M-05} e {@code SIFAP-M-08}: o pagamento sem numero. */
    @Test
    @DisplayName("deve contar os pagamentos sem numero quando o campo unico veio em branco")
    void deve_contar_os_pagamentos_sem_numero_quando_o_campo_unico_veio_em_branco() {
        ensurePartition();

        PaymentMigrationReport report = loader.load(
                List.of(
                        line("", CpfGenerator.next(), "100000", "0", "100000", "N"),
                        line("", CpfGenerator.next(), "100000", "0", "100000", "N"),
                        line("2001", CpfGenerator.next(), "100000", "0", "100000", "N")),
                MIGRATION);

        assertThat(report.withoutNumber()).isEqualTo(2);
    }

    /** {@code SIFAP-M-06}: reexecutar a folha duplica o mes inteiro. */
    @Test
    @DisplayName("deve sinalizar o duplicado sem escolher qual vale quando o CPF repete no periodo")
    void deve_sinalizar_o_duplicado_sem_escolher_qual_vale_quando_o_cpf_repete_no_periodo() {
        ensurePartition();
        String cpf = CpfGenerator.next();

        PaymentMigrationReport report = loader.load(
                List.of(line("3001", cpf, "100000", "0", "100000", "N"), line("3002", cpf, "120000", "0", "120000", "N")),
                MIGRATION);

        assertThat(report.duplicatedInPeriod()).isEqualTo(1);
        assertThat(report.paymentsMigrated()).isEqualTo(1);
        assertThat(report.noPaymentDiscarded()).isFalse();
    }

    /** {@code CALCDSCT.NSP:188} atualiza o desconto e nao recalcula o liquido. */
    @Test
    @DisplayName("deve contar o liquido inconsistente quando nao fecha com bruto menos desconto")
    void deve_contar_o_liquido_inconsistente_quando_nao_fecha_com_bruto_menos_desconto() {
        ensurePartition();

        PaymentMigrationReport report =
                loader.load(List.of(line("4001", CpfGenerator.next(), "100000", "20000", "100000", "N")), MIGRATION);

        assertThat(report.inconsistentNet()).isEqualTo(1);
    }

    /** {@code CALCBENF.NSN:273} grava {@code D} em dezembro, valor fora do dominio. */
    @Test
    @DisplayName("deve inventariar o tipo fora do dominio quando o legado gravou D")
    void deve_inventariar_o_tipo_fora_do_dominio_quando_o_legado_gravou_d() {
        ensurePartition();

        PaymentMigrationReport report =
                loader.load(List.of(line("5001", CpfGenerator.next(), "100000", "0", "100000", "D")), MIGRATION);

        assertThat(report.typeOutOfDomain()).containsKey("type=D");
        assertThat(report.paymentsMigrated()).isEqualTo(1);
    }
}
