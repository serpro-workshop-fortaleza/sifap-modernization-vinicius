package br.gov.sifap.payment.internal.calculation;

import static br.gov.sifap.payment.ProgramFixture.aProgram;
import static org.assertj.core.api.Assertions.assertThat;

import br.gov.sifap.beneficiary.BeneficiaryStatus;
import br.gov.sifap.payment.BenefitInput;
import br.gov.sifap.payment.EligibilityResult;
import br.gov.sifap.socialprogram.SocialProgramStatus;
import br.gov.sifap.socialprogram.SocialProgramType;
import br.gov.sifap.socialprogram.SocialProgramView;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Apuracao de elegibilidade")
class EligibilityCheckerTest {

    private final EligibilityChecker checker = new EligibilityChecker();

    private static BenefitInput input(String region, String income, int dependents, int age) {
        return new BenefitInput("11144477735", "202503", region, new BigDecimal(income), dependents, age);
    }

    @Test
    @DisplayName("deve aprovar quando o beneficiario esta ativo e cumpre as condicoes")
    void deve_aprovar_quando_o_beneficiario_esta_ativo_e_cumpre_as_condicoes() {
        EligibilityResult result = checker.check(
                input("01", "100.00", 0, 40), Optional.of(BeneficiaryStatus.ATIVO), aProgram().build());

        assertThat(result.eligible()).isTrue();
        assertThat(result.reasons()).isEmpty();
        assertThat(result.waivedByRegion()).isFalse();
    }

    /**
     * {@code REQ-PAY-009}. {@code VALELEG.NSN:236} acumula ate dez motivos em
     * {@code #REASON(1:10)} e devolve so o primeiro.
     */
    @Test
    @DisplayName("deve devolver todos os motivos quando varias condicoes falham")
    void deve_devolver_todos_os_motivos_quando_varias_condicoes_falham() {
        SocialProgramView program = aProgram()
                .withAgeRange(18, 65)
                .withMaxPerCapitaIncome("500.00")
                .withEligibilityCode("RD   ")
                .build();

        EligibilityResult result =
                checker.check(input("01", "900.00", 0, 70), Optional.of(BeneficiaryStatus.SUSPENSO), program);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasons()).hasSize(4);
    }

    /**
     * {@code REQ-PAY-005}. {@code VALELEG.NSN:133} deixa a situacao em branco passar e
     * {@code CALCBENF.NSN:180} a recusa. O mesmo beneficiario e elegivel para um e nao para
     * o outro; aqui o criterio e unico.
     */
    @Test
    @DisplayName("deve recusar quando a situacao do beneficiario esta ausente")
    void deve_recusar_quando_a_situacao_do_beneficiario_esta_ausente() {
        EligibilityResult result =
                checker.check(input("01", "100.00", 0, 40), Optional.empty(), aProgram().build());

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("situacao ausente"));
    }

    /**
     * {@code REQ-PAY-008}, nivel {@code PS}: comportamento preservado, concessao marcada.
     */
    @Test
    @DisplayName("deve aprovar por dispensa quando a regiao e 99 mesmo com o beneficiario suspenso")
    void deve_aprovar_por_dispensa_quando_a_regiao_e_99_mesmo_com_o_beneficiario_suspenso() {
        EligibilityResult result = checker.check(
                input("99", "99999.00", 0, 5), Optional.of(BeneficiaryStatus.SUSPENSO), aProgram().build());

        assertThat(result.eligible()).isTrue();
        assertThat(result.waivedByRegion()).isTrue();
    }

    @Test
    @DisplayName("deve recusar quando o programa nao esta ativo")
    void deve_recusar_quando_o_programa_nao_esta_ativo() {
        SocialProgramView program = aProgram().withStatus(SocialProgramStatus.INATIVO).build();

        EligibilityResult result =
                checker.check(input("99", "100.00", 0, 40), Optional.of(BeneficiaryStatus.ATIVO), program);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasons()).containsExactly("programa social nao esta ativo");
    }

    @Test
    @DisplayName("deve recusar quando o programa previdenciario exige idade minima nao atingida")
    void deve_recusar_quando_o_programa_previdenciario_exige_idade_minima_nao_atingida() {
        SocialProgramView program = aProgram().withType(SocialProgramType.PREVIDENCIA).build();

        EligibilityResult result =
                checker.check(input("01", "100.00", 0, 45), Optional.of(BeneficiaryStatus.ATIVO), program);

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("previdenciario"));
    }

    /**
     * {@code REQ-PAY-007}, nivel {@code PS}: o campo do programa e teto <em>per capita</em>
     * e o valor comparado e a renda familiar total ({@code VALELEG.NSN:168}).
     */
    @Test
    @DisplayName("deve comparar a renda familiar total quando o programa declara teto per capita")
    void deve_comparar_a_renda_familiar_total_quando_o_programa_declara_teto_per_capita() {
        SocialProgramView program = aProgram().withMaxPerCapitaIncome("400.00").build();

        EligibilityResult result =
                checker.check(input("01", "1200.00", 3, 40), Optional.of(BeneficiaryStatus.ATIVO), program);

        assertThat(result.eligible()).isFalse();
        assertThat(EligibilityChecker.perCapitaIncomeOf(new BigDecimal("1200.00"), 4))
                .isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("deve recusar quando o programa exige dependente e nao ha nenhum ativo")
    void deve_recusar_quando_o_programa_exige_dependente_e_nao_ha_nenhum_ativo() {
        SocialProgramView program = aProgram().withEligibilityCode("RD   ").build();

        EligibilityResult result =
                checker.check(input("01", "100.00", 0, 40), Optional.of(BeneficiaryStatus.ATIVO), program);

        assertThat(result.reasons()).containsExactly("programa exige ao menos um dependente");
    }
}
