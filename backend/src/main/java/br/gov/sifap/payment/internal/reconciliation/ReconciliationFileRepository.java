package br.gov.sifap.payment.internal.reconciliation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReconciliationFileRepository extends JpaRepository<ReconciliationFile, Long> {

    Optional<ReconciliationFile> findBySha256(String sha256);
}
