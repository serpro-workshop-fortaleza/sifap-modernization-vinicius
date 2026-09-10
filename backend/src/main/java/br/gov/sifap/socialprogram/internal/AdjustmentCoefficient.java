package br.gov.sifap.socialprogram.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Coeficiente do fator de ajuste, com vigencia.
 *
 * <p>Atende {@code REQ-PRG-005}. O valor {@code 0.347215} vem de
 * {@code CADPROG.NSP:124} e nao tem origem em nenhuma fonte do acervo — nem no cabecalho
 * do programa, nem no dicionario, nem no levantamento de 2012.
 *
 * <p>Existe como tabela, e nao como {@code static final}, para ser questionavel. Uma
 * constante no codigo seria mais simples e reproduziria exatamente o defeito: numero sem
 * origem, escondido dentro de um {@code COMPUTE}.
 */
@Entity
@Table(name = "adjustment_coefficient")
public class AdjustmentCoefficient {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "adjustmentCoefficientSeq")
    @SequenceGenerator(
            name = "adjustmentCoefficientSeq",
            sequenceName = "adjustment_coefficient_id_seq",
            allocationSize = 50)
    private Long id;

    @Column(name = "coefficient", nullable = false, precision = 9, scale = 6)
    private BigDecimal coefficient;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "source", nullable = false, length = 120)
    private String source;

    protected AdjustmentCoefficient() {
        // exigido pelo JPA
    }

    public BigDecimal coefficient() {
        return coefficient;
    }

    public LocalDate validFrom() {
        return validFrom;
    }

    public Optional<LocalDate> validTo() {
        return Optional.ofNullable(validTo);
    }

    /** Documentacao viva da lacuna: o texto declara que a origem e desconhecida. */
    public String source() {
        return source;
    }
}
