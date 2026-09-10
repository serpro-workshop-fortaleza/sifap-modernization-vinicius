package br.gov.sifap.socialprogram.internal;

import br.gov.sifap.socialprogram.SocialProgramParameters;
import br.gov.sifap.socialprogram.SocialProgramView;
import br.gov.sifap.socialprogram.SocialProgramView.CalculationBandView;
import br.gov.sifap.socialprogram.SocialProgramView.RegionalParameterView;
import java.util.Comparator;
import java.util.List;

final class SocialProgramMapper {

    private SocialProgramMapper() {
    }

    /** Exatamente o conjunto que VALELEG.NSN:106-111 e CALCBENF.NSN:194-196 leem. */
    static SocialProgramParameters toParameters(SocialProgram program) {
        return new SocialProgramParameters(
                program.code(),
                program.type(),
                program.status(),
                program.amountBase(),
                program.adjustmentFactor(),
                program.eligibilityCode(),
                program.ageMin(),
                program.ageMax(),
                program.maxPerCapitaIncome());
    }

    static SocialProgramView toView(SocialProgram program) {
        List<CalculationBandView> bands = program.bands().stream()
                .sorted(Comparator.comparing(CalculationBand::incomeFrom))
                .map(band -> new CalculationBandView(
                        band.incomeFrom(),
                        band.incomeTo(),
                        band.multiplier(),
                        band.additionalAmount(),
                        band.accumulates()))
                .toList();

        List<RegionalParameterView> regions = program.regions().stream()
                .sorted(Comparator.comparing(RegionalParameter::regionCode))
                .map(region -> new RegionalParameterView(
                        region.regionCode(),
                        region.multiplier(),
                        region.complementAmount(),
                        region.isActive()))
                .toList();

        return new SocialProgramView(
                program.code(),
                program.name(),
                program.acronym(),
                program.type(),
                program.status(),
                program.statusReason(),
                program.amountBase(),
                program.adjustmentFactor(),
                program.maxPerCapitaIncome(),
                program.ageMin(),
                program.ageMax(),
                program.eligibilityCode(),
                program.startedAt(),
                program.closedAt(),
                bands,
                regions);
    }
}
