package br.gov.sifap.payment;

import java.math.BigDecimal;

/**
 * Insumos do calculo do beneficio.
 *
 * @param referencePeriod competencia no formato {@code YYYYMM}
 * @param declaredIncome renda familiar declarada; ver {@code REQ-PAY-007}
 */
public record BenefitInput(
        String cpf,
        String referencePeriod,
        String regionCode,
        BigDecimal declaredIncome,
        int activeDependents,
        int age) {
}
