package br.gov.sifap.payment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Resultado do calculo do beneficio.
 *
 * <p>Carrega os fatores aplicados por exigencia do {@code AC-010.2}: depois de uma folha,
 * e possivel responder quanto cada fator contribuiu para o total pago — pergunta que hoje
 * nao tem resposta.
 *
 * @param appliedFactors fator por tipo, incluindo o ajuste do programa
 */
public record BenefitCalculation(
        BigDecimal amountBase,
        Map<FactorType, BigDecimal> appliedFactors,
        BigDecimal grossAmount,
        BigDecimal thirteenthAmount,
        BigDecimal bonusAmount,
        BigDecimal discountAmount,
        BigDecimal netAmount) {

    public BenefitCalculation {
        appliedFactors = Map.copyOf(appliedFactors);
    }
}
