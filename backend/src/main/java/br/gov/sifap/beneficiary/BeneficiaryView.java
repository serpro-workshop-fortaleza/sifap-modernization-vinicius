package br.gov.sifap.beneficiary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Projecao de leitura de um beneficiario.
 *
 * <p>Existe para que nenhum consumidor alcance a entidade JPA do contexto.
 *
 * @param maskedCpf documento parcialmente oculto ({@code REQ-BEN-017})
 * @param age vazio quando a origem migrada nao trouxe data de nascimento legivel
 * @param status vazio em registro migrado sem situacao confiavel na origem
 */
public record BeneficiaryView(
        String maskedCpf,
        Optional<String> nis,
        String fullName,
        LocalDate birthDate,
        Optional<Integer> age,
        Sex sex,
        Optional<BeneficiaryStatus> status,
        Optional<String> statusReason,
        Optional<String> programCode,
        Optional<BigDecimal> familyIncome,
        AddressData address,
        int activeDependentCount,
        List<DependentView> dependents) {

    public BeneficiaryView {
        dependents = List.copyOf(dependents);
    }
}
