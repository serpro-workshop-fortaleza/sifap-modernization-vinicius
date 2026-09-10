package br.gov.sifap.beneficiary.internal;

import br.gov.sifap.beneficiary.AddressData;
import br.gov.sifap.beneficiary.BeneficiaryStatus;
import br.gov.sifap.beneficiary.Sex;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dados de um beneficiario ja convertidos do formato legado.
 *
 * <p>Campos anulaveis representam valor ausente ou ilegivel na origem. Cada um deles gera
 * uma pendencia de migracao, nunca a recusa do registro.
 */
public record MigratedBeneficiary(
        String cpf,
        String nis,
        String fullName,
        LocalDate birthDate,
        Sex sex,
        BeneficiaryStatus status,
        String programCode,
        BigDecimal familyIncome,
        AddressData address,
        LocalDate registeredAt) {
}
