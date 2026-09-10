package br.gov.sifap.socialprogram;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Projecao de administracao de um programa social. */
public record SocialProgramView(
        String code,
        String name,
        Optional<String> acronym,
        SocialProgramType type,
        SocialProgramStatus status,
        Optional<String> statusReason,
        BigDecimal amountBase,
        BigDecimal adjustmentFactor,
        Optional<BigDecimal> maxPerCapitaIncome,
        int ageMin,
        int ageMax,
        Optional<String> eligibilityCode,
        LocalDate startedAt,
        Optional<LocalDate> closedAt,
        List<CalculationBandView> bands,
        List<RegionalParameterView> regions) {

    public SocialProgramView {
        bands = List.copyOf(bands);
        regions = List.copyOf(regions);
    }

    /** @param incomeTo vazio na faixa aberta no topo */
    public record CalculationBandView(
            BigDecimal incomeFrom,
            Optional<BigDecimal> incomeTo,
            BigDecimal multiplier,
            BigDecimal additionalAmount,
            boolean accumulates) {
    }

    public record RegionalParameterView(
            String regionCode,
            BigDecimal multiplier,
            BigDecimal complementAmount,
            boolean active) {
    }
}
