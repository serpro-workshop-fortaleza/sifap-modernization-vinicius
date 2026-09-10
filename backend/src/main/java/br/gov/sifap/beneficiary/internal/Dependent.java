package br.gov.sifap.beneficiary.internal;

import br.gov.sifap.beneficiary.DependentStatus;
import br.gov.sifap.beneficiary.Relationship;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Dependente de um beneficiario.
 *
 * <p>Nao e raiz de agregado: toda operacao passa por {@link Beneficiary}, que conhece o
 * limite de ativos e verifica duplicidade. Ver ADR-0004.
 *
 * <p>Atende {@code REQ-BEN-010}: a situacao e obrigatoria, ao contrario do legado, em que
 * {@code CADDEPEN.NSP:194-202} grava o dependente sem tocar o campo.
 */
@Entity
@Table(name = "dependent")
public class Dependent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dependentSeq")
    @SequenceGenerator(name = "dependentSeq", sequenceName = "dependent_id_seq", allocationSize = 500)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private Beneficiary beneficiary;

    /** Nulo quando o dependente nao possui CPF ({@code REQ-BEN-011}). */
    @Column(name = "cpf", length = 11)
    private String cpf;

    @Column(name = "full_name", nullable = false, length = 60)
    private String fullName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation", nullable = false, length = 2)
    private Relationship relation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private DependentStatus status;

    @Column(name = "disability", nullable = false)
    private boolean disability;

    protected Dependent() {
        // exigido pelo JPA
    }

    Dependent(
            Beneficiary beneficiary,
            String cpf,
            String fullName,
            LocalDate birthDate,
            Relationship relation,
            DependentStatus status,
            boolean disability) {
        this.beneficiary = beneficiary;
        this.cpf = cpf;
        this.fullName = fullName;
        this.birthDate = birthDate;
        this.relation = relation;
        this.status = status;
        this.disability = disability;
    }

    public boolean isActive() {
        return status == DependentStatus.ATIVO;
    }

    void deactivate() {
        this.status = DependentStatus.INATIVO;
    }

    public Long id() {
        return id;
    }

    public Optional<String> cpf() {
        return Optional.ofNullable(cpf);
    }

    public String fullName() {
        return fullName;
    }

    public Optional<LocalDate> birthDate() {
        return Optional.ofNullable(birthDate);
    }

    public Relationship relation() {
        return relation;
    }

    public DependentStatus status() {
        return status;
    }

    public boolean hasDisability() {
        return disability;
    }
}
