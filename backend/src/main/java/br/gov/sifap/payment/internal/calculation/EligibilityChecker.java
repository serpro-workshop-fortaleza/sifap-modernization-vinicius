package br.gov.sifap.payment.internal.calculation;

import br.gov.sifap.beneficiary.BeneficiaryStatus;
import br.gov.sifap.payment.BenefitInput;
import br.gov.sifap.payment.EligibilityResult;
import br.gov.sifap.socialprogram.SocialProgramStatus;
import br.gov.sifap.socialprogram.SocialProgramType;
import br.gov.sifap.socialprogram.SocialProgramView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Apuracao de elegibilidade.
 *
 * <p>Atende {@code REQ-PAY-004} a {@code REQ-PAY-009}.
 *
 * <p>Unifica um criterio que o legado aplica de duas formas: {@code VALELEG.NSN:133} testa
 * {@code NE 'A'} e depois compara com {@code S}, {@code C}, {@code D} e {@code I}, de modo
 * que um valor em branco nao casa com nenhum e sai sem alterar a elegibilidade; ja
 * {@code CALCBENF.NSN:180} testa {@code NE 'A'} e recusa direto. O mesmo beneficiario e
 * elegivel para um e nao para o outro.
 */
@Component
public class EligibilityChecker {

    /** {@code VALELEG.NSN:123} concede elegibilidade total a esta regiao. */
    private static final String WAIVER_REGION = "99";

    private static final int PENSION_MINIMUM_AGE = 60;
    private static final int EMPLOYMENT_MINIMUM_AGE = 16;
    private static final int EMPLOYMENT_MAXIMUM_AGE = 65;

    public EligibilityResult check(
            BenefitInput input,
            Optional<BeneficiaryStatus> beneficiaryStatus,
            SocialProgramView program) {

        if (program.status() != SocialProgramStatus.ATIVO) {
            return EligibilityResult.ineligible(List.of("programa social nao esta ativo"));
        }

        // REQ-PAY-008, nivel PS: preservado por alterar quem recebe. O que muda e o
        // registro, que torna cada concessao por esta via contavel.
        if (WAIVER_REGION.equals(input.regionCode())) {
            return EligibilityResult.waived();
        }

        List<String> reasons = new ArrayList<>();

        // REQ-PAY-005: criterio unico. Situacao ausente ou diferente de ativa impede.
        if (beneficiaryStatus.orElse(null) != BeneficiaryStatus.ATIVO) {
            reasons.add("beneficiario nao esta ativo: "
                    + beneficiaryStatus.map(Enum::name).orElse("situacao ausente"));
        }

        if (program.ageMin() > 0 && input.age() < program.ageMin()) {
            reasons.add("idade abaixo do minimo do programa");
        }
        if (program.ageMax() > 0 && input.age() > program.ageMax()) {
            reasons.add("idade acima do maximo do programa");
        }

        // REQ-PAY-007, nivel PS: o campo do programa e teto per capita e o valor comparado
        // e a renda familiar total.
        program.maxPerCapitaIncome()
                .filter(ceiling -> input.declaredIncome().compareTo(ceiling) > 0)
                .ifPresent(ceiling -> reasons.add("renda familiar acima do teto do programa"));

        checkProgramTypeRules(input, program, reasons);
        checkEligibilityCode(input, program, reasons);

        return reasons.isEmpty() ? EligibilityResult.approved() : EligibilityResult.ineligible(reasons);
    }

    private static void checkProgramTypeRules(
            BenefitInput input, SocialProgramView program, List<String> reasons) {
        if (program.type() == SocialProgramType.PREVIDENCIA && input.age() < PENSION_MINIMUM_AGE) {
            reasons.add("programa previdenciario exige idade minima de " + PENSION_MINIMUM_AGE);
        }
        if (program.type() == SocialProgramType.TRABALHO
                && (input.age() < EMPLOYMENT_MINIMUM_AGE || input.age() > EMPLOYMENT_MAXIMUM_AGE)) {
            reasons.add("programa de trabalho exige idade entre "
                    + EMPLOYMENT_MINIMUM_AGE + " e " + EMPLOYMENT_MAXIMUM_AGE);
        }
    }

    /**
     * Codigo posicional de elegibilidade.
     *
     * <p>{@code VALELEG.NSN:249-268} interpreta {@code R} na primeira posicao como exigencia
     * de NIS e {@code D} na segunda como exigencia de dependentes. As outras tres posicoes
     * do campo {@code A5} nunca sao interpretadas, e o significado do campo remete ao ticket
     * 4471/2012, ausente do acervo.
     */
    private static void checkEligibilityCode(
            BenefitInput input, SocialProgramView program, List<String> reasons) {
        String code = program.eligibilityCode().orElse("");
        if (code.length() >= 2 && code.charAt(1) == 'D' && input.activeDependents() == 0) {
            reasons.add("programa exige ao menos um dependente");
        }
    }

    /** Exposto para o relatorio de sinalizacao do {@code REQ-PAY-007}. */
    public static BigDecimal perCapitaIncomeOf(BigDecimal familyIncome, int familyMembers) {
        if (familyMembers <= 0) {
            return familyIncome;
        }
        return familyIncome.divide(BigDecimal.valueOf(familyMembers), 2, java.math.RoundingMode.DOWN);
    }
}
