package br.gov.sifap.payment;

import static br.gov.sifap.payment.ProgramFixture.aProgram;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.beneficiary.BeneficiaryStatus;
import br.gov.sifap.payment.internal.calculation.BenefitCalculator;
import br.gov.sifap.payment.internal.calculation.EligibilityChecker;
import br.gov.sifap.shared.exception.DomainRuleException;
import br.gov.sifap.socialprogram.SocialProgramView;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Divergencias deliberadas em relacao ao legado.
 *
 * <p>Cada teste aqui <strong>falharia</strong> contra o SIFAP original. Existem para que a
 * diferenca seja explicita e consultavel, e nao um detalhe que alguem descobre em
 * producao. O criterio da Fatia 4: nenhuma decisao que muda quanto uma pessoa recebe foi
 * corrigida sem estar registrada.
 */
@DisplayName("Divergencias do legado na folha")
class LegacyDivergencePaymentTest {

    private final BenefitCalculator calculator = new BenefitCalculator();
    private final EligibilityChecker checker = new EligibilityChecker();

    private static BenefitInput input(String income, int dependents, int age) {
        return new BenefitInput("11144477735", "202503", "01", new BigDecimal(income), dependents, age);
    }

    /**
     * {@code SIFAP-M-11}. Renda acima da ultima faixa deixa {@code #FACTOR-INCOME} com o
     * valor do beneficiario anterior ({@code BATCHPGT.NSP:380-404}). O legado paga; aqui
     * a folha recusa e diz por que.
     */
    @Test
    @DisplayName("deve falhar em vez de reaproveitar o fator anterior quando a renda excede as faixas")
    void deve_falhar_em_vez_de_reaproveitar_o_fator_anterior_quando_a_renda_excede_as_faixas() {
        SocialProgramView program =
                aProgram().withoutBands().withBand("0.00", "9999.99", "1.0000", "0.00").build();

        assertThatThrownBy(() -> calculator.calculate(input("15000.00", 0, 40), program))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("faixa");
    }

    /**
     * {@code REQ-PAY-013}. {@code CADPROG.NSP:125} gravava o valor base ja multiplicado por
     * {@code 1 + (FACTOR-ADJUST * 0.347215)} e {@code CALCBENF.NSN:261} multiplicava de novo
     * por {@code 1 + FACTOR-ADJUST}. Duas aplicacoes do mesmo ajuste.
     */
    @Test
    @DisplayName("deve aplicar o ajuste uma vez onde o legado aplicava duas")
    void deve_aplicar_o_ajuste_uma_vez_onde_o_legado_aplicava_duas() {
        SocialProgramView program =
                aProgram().withAmountBase("1000.00").withAdjustmentFactor("0.2000").build();

        BenefitCalculation result = calculator.calculate(input("100.00", 0, 40), program);

        assertThat(result.grossAmount()).isEqualByComparingTo("1200.00");
        assertThat(result.grossAmount()).isNotEqualByComparingTo("1440.00");
    }

    /**
     * {@code REQ-PAY-005}. {@code VALELEG.NSN:133} deixa passar a situacao em branco e
     * {@code CALCBENF.NSN:180} a recusa: o mesmo beneficiario tem dois destinos conforme o
     * programa que o avalia.
     */
    @Test
    @DisplayName("deve aplicar um criterio unico de situacao onde o legado tinha dois")
    void deve_aplicar_um_criterio_unico_de_situacao_onde_o_legado_tinha_dois() {
        EligibilityResult result =
                checker.check(input("100.00", 0, 40), Optional.empty(), aProgram().build());

        assertThat(result.eligible()).isFalse();
    }

    /**
     * {@code REQ-PAY-009}. {@code VALELEG.NSN:236} acumula dez motivos e devolve um.
     */
    @Test
    @DisplayName("deve devolver todos os motivos onde o legado devolvia apenas o primeiro")
    void deve_devolver_todos_os_motivos_onde_o_legado_devolvia_apenas_o_primeiro() {
        SocialProgramView program =
                aProgram().withAgeRange(18, 60).withMaxPerCapitaIncome("100.00").build();

        EligibilityResult result =
                checker.check(input("900.00", 0, 70), Optional.of(BeneficiaryStatus.SUSPENSO), program);

        assertThat(result.reasons()).hasSizeGreaterThan(1);
    }

    /**
     * {@code REQ-PAY-008}, nivel {@code PS}. A dispensa da regiao {@code 99}
     * ({@code VALELEG.NSN:123}) permanece, mas deixa de ser invisivel.
     */
    @Test
    @DisplayName("deve marcar a dispensa regional onde o legado apenas seguia adiante")
    void deve_marcar_a_dispensa_regional_onde_o_legado_apenas_seguia_adiante() {
        BenefitInput waived =
                new BenefitInput("11144477735", "202503", "99", new BigDecimal("99999.00"), 0, 3);

        EligibilityResult result =
                checker.check(waived, Optional.of(BeneficiaryStatus.CANCELADO), aProgram().build());

        assertThat(result.eligible()).isTrue();
        assertThat(result.waivedByRegion()).isTrue();
    }

    /**
     * {@code REQ-PAY-011}. O fator regional vinha de constantes no codigo
     * ({@code CALCBENF.NSN:190-201}); agora vem da parametrizacao do programa, com os
     * mesmos valores. Sem parametro, a folha recusa em vez de assumir 1,0.
     */
    @Test
    @DisplayName("deve exigir parametro regional onde o legado tinha constante no codigo")
    void deve_exigir_parametro_regional_onde_o_legado_tinha_constante_no_codigo() {
        BenefitInput other =
                new BenefitInput("11144477735", "202503", "04", new BigDecimal("100.00"), 0, 40);

        assertThatThrownBy(() -> calculator.calculate(other, aProgram().build()))
                .isInstanceOf(DomainRuleException.class);
    }

    /**
     * {@code REQ-PAY-017}, decisao do {@code SIFAP-M-10}. Duas contribuicoes sociais
     * convivem no legado; preserva-se a do {@code CALCBENF}, unica efetivamente executada.
     */
    @Test
    @DisplayName("deve cobrar contribuicao unica de tres por cento acima do limite")
    void deve_cobrar_contribuicao_unica_de_tres_por_cento_acima_do_limite() {
        SocialProgramView program = aProgram().withAmountBase("1000.00").build();
        SocialProgramView small = aProgram().withAmountBase("400.00").build();

        assertThat(calculator.calculate(input("100.00", 0, 40), program).discountAmount())
                .isEqualByComparingTo("30.00");
        assertThat(calculator.calculate(input("100.00", 0, 40), small).discountAmount())
                .isEqualByComparingTo("0.00");
    }
}
