package br.gov.sifap.payment.internal.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro de retorno que a conciliacao nao soube tratar.
 *
 * <p>Atende {@code REQ-REC-008}, {@code REQ-REC-010} e {@code REQ-REC-016}.
 *
 * <p>No legado estas ocorrencias viram uma linha no {@code CMPRINT}
 * ({@code BATCHCON.NSP:183-186}) de uma execucao manual, e nao sobrevivem a ela. Mesmo
 * padrao de {@code beneficiary_migration_issue} da Fatia 2: o que nao se sabe tratar fica
 * contavel.
 */
@Entity
@Table(name = "reconciliation_issue")
class ReconciliationIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reconciliationIssueSeq")
    @SequenceGenerator(
            name = "reconciliationIssueSeq",
            sequenceName = "reconciliation_issue_id_seq",
            allocationSize = 500)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 24)
    private ReconciliationIssueType issueType;

    @Column(name = "cpf", length = 11)
    private String cpf;

    @Column(name = "reference_period", length = 6)
    private String referencePeriod;

    @Column(name = "payment_number", length = 20)
    private String paymentNumber;

    @Column(name = "declared_amount", precision = 11, scale = 2)
    private BigDecimal declaredAmount;

    @Column(name = "return_code", length = 2)
    private String returnCode;

    @Column(name = "detail", nullable = false, length = 200)
    private String detail;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    protected ReconciliationIssue() {
        // exigido pelo JPA
    }

    ReconciliationIssue(
            Long fileId,
            ReconciliationIssueType issueType,
            String cpf,
            String referencePeriod,
            String paymentNumber,
            BigDecimal declaredAmount,
            String returnCode,
            String detail,
            Instant detectedAt) {
        this.fileId = fileId;
        this.issueType = issueType;
        this.cpf = cpf;
        this.referencePeriod = referencePeriod;
        this.paymentNumber = paymentNumber;
        this.declaredAmount = declaredAmount;
        this.returnCode = returnCode;
        this.detail = truncate(detail);
        this.detectedAt = detectedAt;
    }

    private static String truncate(String value) {
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    ReconciliationIssueType issueType() {
        return issueType;
    }

    String cpf() {
        return cpf;
    }

    String detail() {
        return detail;
    }

    Long fileId() {
        return fileId;
    }
}
