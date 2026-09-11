package br.gov.sifap.beneficiary.internal;

import br.gov.sifap.beneficiary.BeneficiaryStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Linha crua da consulta de folha.
 *
 * <p>Tipo de topo, e nao aninhado, porque a expressao de construtor do JPQL referencia a
 * classe pelo nome qualificado.
 */
record PayrollRow(
        String cpf,
        String programCode,
        BeneficiaryStatus status,
        BigDecimal familyIncome,
        String regionCode,
        LocalDate birthDate,
        long activeDependents) {
}
