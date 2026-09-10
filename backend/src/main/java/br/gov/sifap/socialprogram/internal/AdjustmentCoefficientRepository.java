package br.gov.sifap.socialprogram.internal;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdjustmentCoefficientRepository extends JpaRepository<AdjustmentCoefficient, Long> {

    @Query("""
            SELECT c FROM AdjustmentCoefficient c
            WHERE c.validFrom <= :reference
              AND (c.validTo IS NULL OR c.validTo > :reference)
            """)
    Optional<AdjustmentCoefficient> findEffectiveOn(@Param("reference") LocalDate reference);
}
