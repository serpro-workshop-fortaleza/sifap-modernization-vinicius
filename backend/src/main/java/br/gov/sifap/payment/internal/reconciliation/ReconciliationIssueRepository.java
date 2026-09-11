package br.gov.sifap.payment.internal.reconciliation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReconciliationIssueRepository extends JpaRepository<ReconciliationIssue, Long> {

    List<ReconciliationIssue> findByFileId(Long fileId);

    long countByFileIdAndIssueType(Long fileId, ReconciliationIssueType issueType);
}
