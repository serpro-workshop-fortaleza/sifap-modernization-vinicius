package br.gov.sifap.socialprogram;

import static org.assertj.core.api.Assertions.assertThat;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.socialprogram.internal.migration.CatalogMigrationReport;
import br.gov.sifap.socialprogram.internal.migration.SocialProgramLoader;
import br.gov.sifap.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-313 — cobre {@code REQ-PRG-015}.
 *
 * <p>O inventário é o primeiro momento em que se sabe o que existe nos 45 registros:
 * nenhuma fonte do acervo responde isso.
 */
class SocialProgramLoaderIT extends AbstractIntegrationTest {

    private static final Actor MIGRATION = Actor.process("CARGA-CATALOGO");

    @Autowired private SocialProgramLoader loader;
    @Autowired private SocialProgramQuery query;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparBase() {
        jdbcTemplate.update("DELETE FROM calculation_band");
        jdbcTemplate.update("DELETE FROM regional_parameter");
        jdbcTemplate.update("DELETE FROM social_program");
    }

    @Test
    @DisplayName("deve informar quantos programas tem faixas de calculo preenchidas")
    void deve_informar_quantos_tem_faixas() {
        // REQ-PRG-015 / AC-015.1. A estrutura existe em SOCPROG.ddm:69 desde 1997 e
        // CALCBENF.NSN:129-137 carrega os fatores por MOVE no fonte.
        CatalogMigrationReport report = loader.load(
                List.of(
                        programLine("0001", "A", "A", "000060000", "0000000", "N", "N"),
                        programLine("0002", "A", "A", "000060000", "0000000", "S", "N")),
                MIGRATION);

        assertThat(report.programsRead()).isEqualTo(2);
        assertThat(report.withCalculationBands()).isOne();
        assertThat(report.withRegionalParameters()).isZero();
    }

    @Test
    @DisplayName("deve informar quantos programas tem fator de correcao especial")
    void deve_informar_quantos_tem_fator_especial() {
        // REQ-PRG-015 / AC-015.2. O campo BG FACTOR-K nao e lido nem escrito por nenhum
        // programa do acervo, apesar do aviso de autorizacao SENARC no dicionario.
        CatalogMigrationReport report = loader.load(
                List.of(programLineWithSpecialFactor("0001", "000012000")), MIGRATION);

        assertThat(report.withSpecialFactor()).isOne();
    }

    @Test
    @DisplayName("deve sinalizar programa cujo valor base foi gravado ajustado")
    void deve_sinalizar_valor_base_ajustado() {
        // REQ-PRG-015 / AC-015.3. Fator diferente de zero implica que CADPROG.NSP:125
        // multiplicou o valor base antes de grava-lo.
        CatalogMigrationReport report = loader.load(
                List.of(
                        programLine("0001", "A", "A", "000060000", "0020000", "N", "N"),
                        programLine("0002", "A", "A", "000060000", "0000000", "N", "N")),
                MIGRATION);

        assertThat(report.withAdjustedAmount()).isOne();
    }

    @Test
    @DisplayName("deve inventariar valores fora do dominio sem recusar o registro")
    void deve_inventariar_valores_fora_do_dominio() {
        CatalogMigrationReport report = loader.load(
                List.of(programLine("0001", "Z", "X", "000060000", "0000000", "N", "N")), MIGRATION);

        assertThat(report.valuesOutOfDomain())
                .containsEntry("type=Z", 1L)
                .containsEntry("status=X", 1L);
        assertThat(report.noProgramDiscarded()).isTrue();
    }

    @Test
    @DisplayName("deve nao descartar nenhum programa ainda que todos tenham pendencia")
    void deve_nao_descartar_nenhum_programa() {
        CatalogMigrationReport report = loader.load(
                List.of(
                        programLine("0001", "Z", "X", "000000000", "0000000", "N", "N"),
                        programLine("0002", "A", "A", "000060000", "0000000", "N", "N")),
                MIGRATION);

        assertThat(report.noProgramDiscarded()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM social_program", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("deve migrar o programa consultavel apos a carga")
    void deve_migrar_o_programa_consultavel() {
        loader.load(List.of(programLine("0001", "A", "A", "000060000", "0000000", "N", "N")), MIGRATION);

        assertThat(query.parametersOf("0001"))
                .get()
                .satisfies(parameters -> {
                    assertThat(parameters.amountBase()).isEqualByComparingTo("600.00");
                    assertThat(parameters.type()).isEqualTo(SocialProgramType.ASSISTENCIA);
                });
    }

    /** Layout posicional da extracao de SOCPROG. */
    private static String programLine(
            String code, String type, String status, String amount,
            String adjustFactor, String hasBands, String hasRegions) {
        return "1"
                + pad(code, 4)
                + pad("Bolsa Exemplo", 60)
                + pad(type, 1)
                + pad(status, 1)
                + pad(amount, 9)
                + pad(adjustFactor, 7)
                + pad("0000000000", 9)
                + pad("000030000", 9)
                + pad("000", 3)
                + pad("065", 3)
                + pad("RD", 5)
                + "20200101"
                + pad(hasBands, 1)
                + pad(hasRegions, 1);
    }

    private static String programLineWithSpecialFactor(String code, String specialFactor) {
        return "1"
                + pad(code, 4)
                + pad("Bolsa Exemplo", 60)
                + pad("A", 1)
                + pad("A", 1)
                + pad("000060000", 9)
                + pad("0000000", 7)
                + pad(specialFactor, 9)
                + pad("000030000", 9)
                + pad("000", 3)
                + pad("065", 3)
                + pad("RD", 5)
                + "20200101"
                + pad("N", 1)
                + pad("N", 1);
    }

    private static String pad(String value, int size) {
        String safe = value == null ? "" : value;
        if (safe.length() >= size) {
            return safe.substring(0, size);
        }
        return safe + " ".repeat(size - safe.length());
    }
}
