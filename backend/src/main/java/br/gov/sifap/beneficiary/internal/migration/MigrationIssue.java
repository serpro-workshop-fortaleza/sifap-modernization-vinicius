package br.gov.sifap.beneficiary.internal.migration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Pendencia detectada na carga inicial.
 *
 * <p>Fica fora do agregado de negocio de proposito: um valor de situacao {@code PENDENTE}
 * obrigaria a Fatia 4 a conhecer um estado que so existe por causa da migracao.
 */
@Entity
@Table(name = "beneficiary_migration_issue")
public class MigrationIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "migrationIssueSeq")
    @SequenceGenerator(
            name = "migrationIssueSeq",
            sequenceName = "beneficiary_migration_issue_id_seq",
            allocationSize = 500)
    private Long id;

    @Column(name = "beneficiary_id")
    private Long beneficiaryId;

    @Column(name = "source_cpf", nullable = false, length = 20)
    private String sourceCpf;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 40)
    private MigrationIssueType issueType;

    @Column(name = "field_name", length = 40)
    private String fieldName;

    @Column(name = "original_value", length = 120)
    private String originalValue;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    protected MigrationIssue() {
        // exigido pelo JPA
    }

    MigrationIssue(
            String sourceCpf,
            MigrationIssueType issueType,
            String fieldName,
            String originalValue,
            Instant detectedAt) {
        this.sourceCpf = sourceCpf;
        this.issueType = issueType;
        this.fieldName = fieldName;
        this.originalValue = truncate(originalValue);
        this.detectedAt = detectedAt;
    }

    void linkTo(Long beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public MigrationIssueType issueType() {
        return issueType;
    }

    public String fieldName() {
        return fieldName;
    }

    public String originalValue() {
        return originalValue;
    }

    public String sourceCpf() {
        return sourceCpf;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 120 ? value : value.substring(0, 120);
    }
}
