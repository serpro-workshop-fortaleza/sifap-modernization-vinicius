package br.gov.sifap.socialprogram.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Faixa de renda com fator e adicional.
 *
 * <p>Atende {@code REQ-PRG-010}. A estrutura existe em {@code SOCPROG.ddm:69} desde 1997,
 * vazia, enquanto {@code CALCBENF.NSN:129-137} carrega cinco fatores por {@code MOVE} no
 * codigo-fonte.
 */
@Entity
@Table(name = "calculation_band")
public class CalculationBand {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "calculationBandSeq")
    @SequenceGenerator(
            name = "calculationBandSeq",
            sequenceName = "calculation_band_id_seq",
            allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "social_program_id", nullable = false)
    private SocialProgram socialProgram;

    @Column(name = "income_from", nullable = false, precision = 9, scale = 2)
    private BigDecimal incomeFrom;

    /** Nulo na faixa aberta no topo. */
    @Column(name = "income_to", precision = 9, scale = 2)
    private BigDecimal incomeTo;

    @Column(name = "multiplier", nullable = false, precision = 7, scale = 4)
    private BigDecimal multiplier;

    @Column(name = "additional_amount", nullable = false, precision = 9, scale = 2)
    private BigDecimal additionalAmount;

    @Column(name = "accumulates", nullable = false)
    private boolean accumulates;

    protected CalculationBand() {
        // exigido pelo JPA
    }

    CalculationBand(
            SocialProgram socialProgram,
            BigDecimal incomeFrom,
            BigDecimal incomeTo,
            BigDecimal multiplier,
            BigDecimal additionalAmount,
            boolean accumulates) {
        this.socialProgram = socialProgram;
        this.incomeFrom = incomeFrom;
        this.incomeTo = incomeTo;
        this.multiplier = multiplier;
        this.additionalAmount = additionalAmount;
        this.accumulates = accumulates;
    }

    /** Verdadeiro quando as duas faixas compartilham qualquer ponto de renda. */
    boolean overlaps(CalculationBand other) {
        boolean endsBeforeOther = incomeTo != null && incomeTo.compareTo(other.incomeFrom) <= 0;
        boolean startsAfterOther =
                other.incomeTo != null && other.incomeTo.compareTo(incomeFrom) <= 0;
        return !endsBeforeOther && !startsAfterOther;
    }

    public BigDecimal incomeFrom() {
        return incomeFrom;
    }

    public Optional<BigDecimal> incomeTo() {
        return Optional.ofNullable(incomeTo);
    }

    public BigDecimal multiplier() {
        return multiplier;
    }

    public BigDecimal additionalAmount() {
        return additionalAmount;
    }

    public boolean accumulates() {
        return accumulates;
    }
}
