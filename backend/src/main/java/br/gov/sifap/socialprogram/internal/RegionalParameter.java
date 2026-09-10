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
import java.util.Set;

/**
 * Multiplicador e complemento por regiao.
 *
 * <p>Atende {@code REQ-PRG-011}. A estrutura existe em {@code SOCPROG.ddm:86} desde 2002,
 * enquanto {@code CALCBENF.NSN:99-125} carrega 27 fatores rotulados por unidade federativa
 * e os indexa por um codigo de regiao que vale de 1 a 5.
 */
@Entity
@Table(name = "regional_parameter")
public class RegionalParameter {

    /** Cinco regioes mais a especial, conforme {@code SOCPROG.ddm:89-90}. */
    static final Set<String> VALID_REGIONS = Set.of("01", "02", "03", "04", "05", "99");

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "regionalParameterSeq")
    @SequenceGenerator(
            name = "regionalParameterSeq",
            sequenceName = "regional_parameter_id_seq",
            allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "social_program_id", nullable = false)
    private SocialProgram socialProgram;

    @Column(name = "region_code", nullable = false, length = 2)
    private String regionCode;

    @Column(name = "multiplier", nullable = false, precision = 7, scale = 4)
    private BigDecimal multiplier;

    @Column(name = "complement_amount", nullable = false, precision = 9, scale = 2)
    private BigDecimal complementAmount;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected RegionalParameter() {
        // exigido pelo JPA
    }

    RegionalParameter(
            SocialProgram socialProgram,
            String regionCode,
            BigDecimal multiplier,
            BigDecimal complementAmount,
            boolean active) {
        this.socialProgram = socialProgram;
        this.regionCode = regionCode;
        this.multiplier = multiplier;
        this.complementAmount = complementAmount;
        this.active = active;
    }

    public String regionCode() {
        return regionCode;
    }

    public BigDecimal multiplier() {
        return multiplier;
    }

    public BigDecimal complementAmount() {
        return complementAmount;
    }

    public boolean isActive() {
        return active;
    }
}
