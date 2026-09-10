package br.gov.sifap.beneficiary.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    @EntityGraph(attributePaths = "dependents")
    Optional<Beneficiary> findByCpf(String cpf);

    @EntityGraph(attributePaths = "dependents")
    Optional<Beneficiary> findByNis(String nis);

    boolean existsByCpf(String cpf);

    boolean existsByNis(String nis);
}
