package br.gov.sifap.socialprogram;

import java.math.BigDecimal;
import java.util.List;

/** Conjunto completo de parametros regionais de um programa. */
public record ReplaceRegionalParametersCommand(List<RegionData> regions) {

    public ReplaceRegionalParametersCommand {
        regions = List.copyOf(regions);
    }

    public record RegionData(
            String regionCode,
            BigDecimal multiplier,
            BigDecimal complementAmount,
            boolean active) {
    }
}
