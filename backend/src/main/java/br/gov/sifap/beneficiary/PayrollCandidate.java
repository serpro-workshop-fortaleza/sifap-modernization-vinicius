package br.gov.sifap.beneficiary;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Beneficiario candidato a um ciclo de folha.
 *
 * <p>Projecao reduzida, separada de {@link BeneficiaryView}: a folha nao precisa de nome,
 * endereco completo nem lista de dependentes, e trafegar isso por 3,8 milhoes de registros
 * seria carregar dado pessoal sem uso.
 *
 * @param cpf documento sem mascara; o consumidor e processo, nao pessoa
 * @param age vazio quando o registro migrado nao trouxe data de nascimento legivel
 */
public record PayrollCandidate(
        String cpf,
        String programCode,
        Optional<BeneficiaryStatus> status,
        Optional<BigDecimal> familyIncome,
        String regionCode,
        int activeDependents,
        Optional<Integer> age) {
}
