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
import java.time.Clock;
import java.time.Instant;

/**
 * Arquivo de retorno processado, identificado pelo resumo do conteudo.
 *
 * <p>Atende {@code REQ-REC-003}. {@code PAYMENT.ddm:110} declara
 * {@code HA HASH-RETURN-FILE A 64 N SHA-256 RETURN FILE} desde 17/11/2015 e nenhum
 * programa o grava.
 *
 * <p>O nome nao serve como identidade: {@code BATCHCON.NSP:139-141} registra que o nome
 * digitado na tela e documentacao, porque a ligacao real e a {@code DD CMWKF01} do JCL.
 */
@Entity
@Table(name = "reconciliation_file")
class ReconciliationFile {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reconciliationFileSeq")
    @SequenceGenerator(
            name = "reconciliationFileSeq",
            sequenceName = "reconciliation_file_id_seq",
            allocationSize = 50)
    private Long id;

    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;

    @Column(name = "declared_name", nullable = false, length = 120)
    private String declaredName;

    @Column(name = "reference_period", nullable = false, length = 6)
    private String referencePeriod;

    @Column(name = "records_read", nullable = false)
    private int recordsRead;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ReconciliationFileStatus status;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "processed_by", nullable = false, length = 50)
    private String processedBy;

    protected ReconciliationFile() {
        // exigido pelo JPA
    }

    static ReconciliationFile start(
            String sha256, String declaredName, String referencePeriod, String actorId, Clock clock) {
        ReconciliationFile file = new ReconciliationFile();
        file.sha256 = sha256;
        file.declaredName = declaredName;
        file.referencePeriod = referencePeriod;
        file.status = ReconciliationFileStatus.EM_ANDAMENTO;
        file.processedAt = clock.instant();
        file.processedBy = actorId;
        return file;
    }

    void complete(int recordsRead) {
        this.recordsRead = recordsRead;
        this.status = ReconciliationFileStatus.CONCLUIDO;
    }

    void interrupt(int recordsRead) {
        this.recordsRead = recordsRead;
        this.status = ReconciliationFileStatus.INTERROMPIDO;
    }

    boolean isCompleted() {
        return status == ReconciliationFileStatus.CONCLUIDO;
    }

    Long id() {
        return id;
    }

    String sha256() {
        return sha256;
    }

    String declaredName() {
        return declaredName;
    }

    String referencePeriod() {
        return referencePeriod;
    }

    int recordsRead() {
        return recordsRead;
    }

    ReconciliationFileStatus status() {
        return status;
    }

    Instant processedAt() {
        return processedAt;
    }
}
