package br.gov.sifap.payment.internal.calculation;

import static br.gov.sifap.payment.ProgramFixture.aProgram;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.payment.BenefitCalculation;
import br.gov.sifap.payment.BenefitInput;
import br.gov.sifap.payment.FactorType;
import br.gov.sifap.shared.exception.DomainRuleException;
import br.gov.sifap.socialprogram.SocialProgramType;
import br.gov.sifap.socialprogram.SocialProgramView;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Calculo do beneficio")
class BenefitCalculatorTest {

    private final BenefitCalculator calculator = new BenefitCalculator();

    private static BenefitInput input(String period, String region, String income, int dependents, int age) {
        return new BenefitInput("11144477735", period, region, new BigDecimal(income), dependents, age);
    }

    @Test
    @DisplayName("deve multiplicar os cinco fatores quando todos estao parametrizados")
    void deve_multiplicar_os_cinco_fatores_quando_todos_estao_parametrizados() {
        SocialProgramView program = aProgram()
                .withAmountBase("1000.00")
                .withRegion("02", "1.2000", "0.00")
                .withoutBands()
                .withBand("0.00", "2000.00", "0.9000", "0.00")
                .withAdjustmentFactor("0.1000")
                .build();

        // 1000 * 1.2 (regiao) * 1.10 (2 dependentes) * 0.9 (renda) * 1.00 (idade 30) * 1.1 (ajuste)
        BenefitCalculation result = calculator.calculate(input("202503", "02", "1500.00", 2, 30), program);

        assertThat(result.appliedFactors())
                .containsEntry(FactorType.REGIONAL, new BigDecimal("1.2000"))
                .containsEntry(FactorType.RENDA, new BigDecimal("0.9000"))
                .containsEntry(FactorType.AJUSTE_DO_PROGRAMA, new BigDecimal("1.1000"));
        assertThat(result.grossAmount()).isEqualByComparingTo("1306.80");
    }

    @Test
    @DisplayName("deve aplicar o fator de ajuste uma unica vez quando o programa o declara")
    void deve_aplicar_o_fator_de_ajuste_uma_unica_vez_quando_o_programa_o_declara() {
        SocialProgramView program = aProgram()
                .withAmountBase("1000.00")
                .withAdjustmentFactor("0.5000")
                .build();

        BenefitCalculation result = calculator.calculate(input("202503", "01", "100.00", 0, 30), program);

        // REQ-PAY-013: CADPROG.NSP:125 ja gravava o base ajustado e CALCBENF.NSN:261
        // multiplicava de novo, produzindo 1.5 * 1.5 = 2.25.
        assertThat(result.appliedFactors()).containsEntry(FactorType.AJUSTE_DO_PROGRAMA, new BigDecimal("1.5000"));
        assertThat(result.grossAmount()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("deve truncar o centavo quando o produto tem fracao")
    void deve_truncar_o_centavo_quando_o_produto_tem_fracao() {
        SocialProgramView program = aProgram()
                .withAmountBase("10.00")
                .withoutBands()
                .withBand("0.00", "9999.99", "0.6666", "0.00")
                .build();

        BenefitCalculation result = calculator.calculate(input("202503", "01", "100.00", 0, 30), program);

        // 10 * 0.6666 = 6.666; REQ-PAY-014 preserva o truncamento, nao o arredondamento.
        assertThat(result.grossAmount()).isEqualByComparingTo("6.66");
    }

    @Test
    @DisplayName("deve somar decimo terceiro e abono quando o periodo e dezembro e o programa e assistencial")
    void deve_somar_decimo_terceiro_e_abono_quando_o_periodo_e_dezembro_e_o_programa_e_assistencial() {
        SocialProgramView program = aProgram().withAmountBase("1000.00").build();

        BenefitCalculation december = calculator.calculate(input("202512", "01", "100.00", 0, 30), program);
        BenefitCalculation november = calculator.calculate(input("202511", "01", "100.00", 0, 30), program);

        assertThat(november.thirteenthAmount()).isEqualByComparingTo("0.00");
        assertThat(december.thirteenthAmount()).isEqualByComparingTo("1000.00");
        assertThat(december.bonusAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("deve omitir o abono quando o programa nao e assistencial")
    void deve_omitir_o_abono_quando_o_programa_nao_e_assistencial() {
        SocialProgramView program =
                aProgram().withType(SocialProgramType.PREVIDENCIA).withAmountBase("1000.00").build();

        BenefitCalculation result = calculator.calculate(input("202512", "01", "100.00", 0, 30), program);

        assertThat(result.bonusAmount()).isEqualByComparingTo("0.00");
        assertThat(result.thirteenthAmount()).isEqualByComparingTo("1000.00");
    }

    @ParameterizedTest(name = "{0} dependentes -> fator {1}")
    @CsvSource({"0,1.00", "1,1.05", "2,1.10", "3,1.13", "4,1.16", "5,1.18", "9,1.26"})
    @DisplayName("deve escalonar o fator familiar quando ha dependentes ativos")
    void deve_escalonar_o_fator_familiar_quando_ha_dependentes_ativos(int dependents, String expected) {
        assertThat(FamilyFactor.of(dependents)).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "idade {0} -> fator {1}")
    @CsvSource({"17,1.05", "18,1.00", "59,1.00", "60,1.10", "64,1.10", "65,1.15", "80,1.15"})
    @DisplayName("deve escalonar o fator etario quando a idade muda de faixa")
    void deve_escalonar_o_fator_etario_quando_a_idade_muda_de_faixa(int age, String expected) {
        assertThat(AgeFactor.of(age)).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("deve recusar o calculo quando a renda nao cai em nenhuma faixa")
    void deve_recusar_o_calculo_quando_a_renda_nao_cai_em_nenhuma_faixa() {
        SocialProgramView program = aProgram().withoutBands().withBand("0.00", "500.00", "1.0000", "0.00").build();

        assertThatThrownBy(() -> calculator.calculate(input("202503", "01", "9000.00", 0, 30), program))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("faixa");
    }

    @Test
    @DisplayName("deve recusar o calculo quando o programa nao parametriza a regiao")
    void deve_recusar_o_calculo_quando_o_programa_nao_parametriza_a_regiao() {
        SocialProgramView program = aProgram().build();

        assertThatThrownBy(() -> calculator.calculate(input("202503", "05", "100.00", 0, 30), program))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("regiao 05");
    }

    /**
     * A defesa contra o defeito mais grave do acervo.
     *
     * <p>{@code BATCHPGT.NSP:380-404} deixa {@code #FACTOR-INCOME} com o valor do
     * beneficiario anterior quando a renda passa da ultima faixa. Dois beneficiarios
     * identicos recebem valores diferentes conforme a ordem de leitura.
     */
    @ParameterizedTest(name = "renda {0} apos renda {1}")
    @MethodSource("incomePairs")
    @DisplayName("deve produzir o mesmo valor quando o mesmo beneficiario e calculado apos outro")
    void deve_produzir_o_mesmo_valor_quando_o_mesmo_beneficiario_e_calculado_apos_outro(
            String income, String previousIncome) {
        SocialProgramView program = aProgram()
                .withoutBands()
                .withBand("0.00", "1000.00", "1.0000", "0.00")
                .withBand("1000.01", "5000.00", "0.7000", "0.00")
                .build();

        BenefitInput target = input("202503", "01", income, 0, 30);
        BigDecimal isolated = calculator.calculate(target, program).grossAmount();

        calculator.calculate(input("202503", "01", previousIncome, 3, 70), program);
        BigDecimal afterOther = calculator.calculate(target, program).grossAmount();

        assertThat(afterOther).isEqualByComparingTo(isolated);
    }

    private static Stream<Arguments> incomePairs() {
        return Stream.of(
                Arguments.of("500.00", "4000.00"),
                Arguments.of("4000.00", "500.00"),
                Arguments.of("1000.01", "999.99"));
    }
}
