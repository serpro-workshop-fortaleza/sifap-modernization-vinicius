package br.gov.sifap.payment.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface CorrectionIndexRepository extends JpaRepository<CorrectionIndex, Long> {

    Optional<CorrectionIndex> findByPeriod(String period);

    List<CorrectionIndex> findByPeriodBetweenOrderByPeriod(String from, String to);
}
