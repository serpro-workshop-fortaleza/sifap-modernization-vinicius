package br.gov.sifap.payment;

import br.gov.sifap.socialprogram.SocialProgramStatus;
import br.gov.sifap.socialprogram.SocialProgramType;
import br.gov.sifap.socialprogram.SocialProgramView;
import br.gov.sifap.socialprogram.SocialProgramView.CalculationBandView;
import br.gov.sifap.socialprogram.SocialProgramView.RegionalParameterView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Montagem de programas sociais para teste.
 *
 * <p>Sem isto, cada teste repete dezessete argumentos e a intencao some no ruido.
 */
public final class ProgramFixture {

    private String code = "0001";
    private SocialProgramType type = SocialProgramType.ASSISTENCIA;
    private SocialProgramStatus status = SocialProgramStatus.ATIVO;
    private BigDecimal amountBase = new BigDecimal("1000.00");
    private BigDecimal adjustmentFactor = BigDecimal.ZERO;
    private Optional<BigDecimal> maxPerCapitaIncome = Optional.empty();
    private int ageMin;
    private int ageMax;
    private Optional<String> eligibilityCode = Optional.empty();
    private final List<CalculationBandView> bands = new ArrayList<>();
    private final List<RegionalParameterView> regions = new ArrayList<>();

    private ProgramFixture() {
    }

    public static ProgramFixture aProgram() {
        return new ProgramFixture()
                .withRegion("01", "1.0000", "0.00")
                .withBand("0.00", "9999.99", "1.0000", "0.00");
    }

    public ProgramFixture withCode(String value) {
        this.code = value;
        return this;
    }

    public ProgramFixture withType(SocialProgramType value) {
        this.type = value;
        return this;
    }

    public ProgramFixture withStatus(SocialProgramStatus value) {
        this.status = value;
        return this;
    }

    public ProgramFixture withAmountBase(String value) {
        this.amountBase = new BigDecimal(value);
        return this;
    }

    public ProgramFixture withAdjustmentFactor(String value) {
        this.adjustmentFactor = new BigDecimal(value);
        return this;
    }

    public ProgramFixture withMaxPerCapitaIncome(String value) {
        this.maxPerCapitaIncome = Optional.of(new BigDecimal(value));
        return this;
    }

    public ProgramFixture withAgeRange(int min, int max) {
        this.ageMin = min;
        this.ageMax = max;
        return this;
    }

    public ProgramFixture withEligibilityCode(String value) {
        this.eligibilityCode = Optional.of(value);
        return this;
    }

    public ProgramFixture withRegion(String regionCode, String multiplier, String complement) {
        this.regions.add(new RegionalParameterView(
                regionCode, new BigDecimal(multiplier), new BigDecimal(complement), true));
        return this;
    }

    public ProgramFixture withoutRegions() {
        this.regions.clear();
        return this;
    }

    public ProgramFixture withBand(String from, String to, String multiplier, String additional) {
        this.bands.add(new CalculationBandView(
                new BigDecimal(from),
                to == null ? Optional.empty() : Optional.of(new BigDecimal(to)),
                new BigDecimal(multiplier),
                new BigDecimal(additional),
                false));
        return this;
    }

    public ProgramFixture withoutBands() {
        this.bands.clear();
        return this;
    }

    public SocialProgramView build() {
        return new SocialProgramView(
                code,
                "Programa de teste",
                Optional.empty(),
                type,
                status,
                Optional.empty(),
                amountBase,
                adjustmentFactor,
                maxPerCapitaIncome,
                ageMin,
                ageMax,
                eligibilityCode,
                LocalDate.of(2020, 1, 1),
                Optional.empty(),
                List.copyOf(bands),
                List.copyOf(regions));
    }
}
