package br.gov.sifap.beneficiary.internal;

import br.gov.sifap.beneficiary.BeneficiaryView;
import br.gov.sifap.beneficiary.DependentView;
import br.gov.sifap.shared.document.Cpf;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Converte o agregado em projecao de leitura, sempre com documento mascarado. */
final class BeneficiaryViewMapper {

    private BeneficiaryViewMapper() {
    }

    static BeneficiaryView toView(Beneficiary beneficiary, LocalDate today) {
        List<DependentView> dependents = beneficiary.dependents().stream()
                .map(BeneficiaryViewMapper::toView)
                .toList();

        return new BeneficiaryView(
                Cpf.mask(beneficiary.cpf()),
                beneficiary.nis(),
                beneficiary.fullName(),
                beneficiary.birthDate(),
                beneficiary.ageAt(today).stream().boxed().findFirst(),
                beneficiary.sex(),
                beneficiary.status(),
                beneficiary.statusReason(),
                beneficiary.programCode(),
                beneficiary.familyIncome(),
                beneficiary.address(),
                beneficiary.activeDependentCount(),
                dependents);
    }

    private static DependentView toView(Dependent dependent) {
        Optional<String> maskedCpf = dependent.cpf().map(Cpf::mask);
        return new DependentView(
                maskedCpf,
                dependent.fullName(),
                dependent.birthDate(),
                dependent.relation(),
                dependent.status(),
                dependent.hasDisability());
    }
}
