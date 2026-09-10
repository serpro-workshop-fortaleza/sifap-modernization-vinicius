package br.gov.sifap.beneficiary;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados de inclusao de beneficiario.
 *
 * @param sex apenas {@code M} ou {@code F} sao aceitos no cadastro ({@code REQ-BEN-002})
 */
public record RegisterBeneficiaryCommand(
        String cpf,
        String nis,
        String fullName,
        LocalDate birthDate,
        Sex sex,
        String programCode,
        BigDecimal familyIncome,
        AddressData address,
        String phoneMobile,
        String rgNumber) {
}
