package br.gov.sifap.payment.internal.calculation;

import br.gov.sifap.payment.BenefitCalculation;
import br.gov.sifap.payment.BenefitInput;
import br.gov.sifap.payment.FactorType;
import br.gov.sifap.socialprogram.SocialProgramType;
import br.gov.sifap.socialprogram.SocialProgramView;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Calculo do beneficio mensal.
 *
 * <p>Atende {@code REQ-PAY-010}, {@code REQ-PAY-013}, {@code REQ-PAY-014} e
 * {@code REQ-PAY-015}.
 *
 * <p><strong>Nao persiste.</strong> E a correcao que resolve o {@code SIFAP-M-05} e o
 * {@code SIFAP-M-08} ao mesmo tempo: {@code CALCBENF.NSN:319} grava o pagamento porque o
 * programa foi convertido de online para subprograma em 2011 e a gravacao veio junto, e
 * esse pagamento e justamente o que nunca recebe {@code NUM-PAYMENT}.
 *
 * <p>A formula multiplicativa de cinco fatores e preservada ({@code SIFAP-M-09}). A
 * {@code RN-013} documenta uma formula aditiva; esta e a que roda.
 */
@Component
public class BenefitCalculator {

    private static final int DECEMBER = 12;
    private static final BigDecimal BONUS_RATE = new BigDecimal("0.15");
    private static final BigDecimal SOCIAL_CONTRIBUTION_THRESHOLD = new BigDecimal("500.00");
    private static final BigDecimal SOCIAL_CONTRIBUTION_RATE = new BigDecimal("0.03");

    public BenefitCalculation calculate(BenefitInput input, SocialProgramView program) {
        Map<FactorType, BigDecimal> factors = new EnumMap<>(FactorType.class);
        factors.put(FactorType.REGIONAL, RegionalFactor.of(input.regionCode(), program.regions()));
        factors.put(FactorType.FAMILIAR, FamilyFactor.of(input.activeDependents()));
        factors.put(FactorType.RENDA, IncomeFactor.of(input.declaredIncome(), program.bands()));
        factors.put(FactorType.ETARIO, AgeFactor.of(input.age()));

        // REQ-PAY-013: uma unica aplicacao. CADPROG.NSP:125 gravava o valor base ja
        // multiplicado e CALCBENF.NSN:261 multiplicava de novo; a Fatia 3 corrigiu a
        // gravacao e este e o outro lado.
        BigDecimal adjustment = BigDecimal.ONE.add(program.adjustmentFactor());
        factors.put(FactorType.AJUSTE_DO_PROGRAMA, adjustment);

        BigDecimal benefit = program.amountBase();
        for (BigDecimal factor : factors.values()) {
            benefit = benefit.multiply(factor);
        }
        benefit = MonetaryScale.truncate(benefit);

        BigDecimal thirteenth = BigDecimal.ZERO;
        BigDecimal bonus = BigDecimal.ZERO;
        BigDecimal gross = benefit;

        if (isDecember(input.referencePeriod())) {
            thirteenth = thirteenthOf(program, factors);
            gross = gross.add(thirteenth);

            if (program.type() == SocialProgramType.ASSISTENCIA) {
                bonus = MonetaryScale.truncate(benefit.multiply(BONUS_RATE));
                gross = gross.add(bonus);
            }
        }

        BigDecimal discount = socialContributionOf(gross);
        BigDecimal net = MonetaryScale.truncate(gross.subtract(discount).max(BigDecimal.ZERO));

        return new BenefitCalculation(
                program.amountBase(), factors, gross, thirteenth, bonus, discount, net);
    }

    /**
     * Decimo terceiro.
     *
     * <p>O comentario de {@code CALCBENF.NSN:268-271} descreve
     * {@code AMT_BASE * FACTOR_REGION * (ACTIVE_MONTHS/12)}, e o codigo usa o fator etario
     * no lugar dos meses ativos. Preserva-se o codigo.
     */
    private static BigDecimal thirteenthOf(
            SocialProgramView program, Map<FactorType, BigDecimal> factors) {
        return MonetaryScale.truncate(program.amountBase()
                .multiply(factors.get(FactorType.REGIONAL))
                .multiply(factors.get(FactorType.ETARIO)));
    }

    /**
     * Contribuicao social.
     *
     * <p>Atende {@code REQ-PAY-017}, decisao do {@code SIFAP-M-10}. Duas contribuicoes
     * convivem no legado: quatro faixas de 3% a 9% em {@code CALCDSCT.NSP:62-69} e 3% fixo
     * acima de R$ 500,00 em {@code CALCBENF.NSN:344-350}. Preserva-se a do
     * {@code CALCBENF}, unico caminho efetivamente executado pela folha.
     */
    private static BigDecimal socialContributionOf(BigDecimal gross) {
        if (gross.compareTo(SOCIAL_CONTRIBUTION_THRESHOLD) <= 0) {
            return BigDecimal.ZERO;
        }
        return MonetaryScale.truncate(gross.multiply(SOCIAL_CONTRIBUTION_RATE));
    }

    private static boolean isDecember(String referencePeriod) {
        return Integer.parseInt(referencePeriod.substring(4, 6)) == DECEMBER;
    }
}
