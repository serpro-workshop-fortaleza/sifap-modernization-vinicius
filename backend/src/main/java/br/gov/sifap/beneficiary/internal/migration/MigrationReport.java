package br.gov.sifap.beneficiary.internal.migration;

import java.util.Map;

/**
 * Resultado da carga inicial.
 *
 * <p>A igualdade entre lidos e migrados e o que o {@code AC-020.3} exige: nenhum
 * beneficiario descartado.
 */
public record MigrationReport(
        int beneficiariesRead,
        int beneficiariesMigrated,
        int dependentsRead,
        int dependentsMigrated,
        Map<MigrationIssueType, Long> issuesByType) {

    public MigrationReport {
        issuesByType = Map.copyOf(issuesByType);
    }

    public boolean noBeneficiaryDiscarded() {
        return beneficiariesRead == beneficiariesMigrated;
    }

    public long issuesOf(MigrationIssueType type) {
        return issuesByType.getOrDefault(type, 0L);
    }

    public long totalIssues() {
        return issuesByType.values().stream().mapToLong(Long::longValue).sum();
    }
}
