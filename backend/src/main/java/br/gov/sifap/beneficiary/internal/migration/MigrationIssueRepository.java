package br.gov.sifap.beneficiary.internal.migration;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MigrationIssueRepository extends JpaRepository<MigrationIssue, Long> {

    List<MigrationIssue> findByIssueType(MigrationIssueType issueType);

    /** Atende {@code REQ-BEN-021}: cada valor distinto encontrado, com sua contagem. */
    @Query("""
            SELECT new br.gov.sifap.beneficiary.internal.migration.DomainInventoryEntry(
                       i.fieldName, i.originalValue, count(i))
            FROM MigrationIssue i
            WHERE i.issueType = :issueType
            GROUP BY i.fieldName, i.originalValue
            ORDER BY count(i) DESC
            """)
    List<DomainInventoryEntry> inventoryOf(MigrationIssueType issueType);
}
