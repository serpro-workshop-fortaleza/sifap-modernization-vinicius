package br.gov.sifap.socialprogram;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dados de inclusao de programa social. */
public record RegisterSocialProgramCommand(
        String code,
        String name,
        String acronym,
        SocialProgramType type,
        BigDecimal amountBase,
        BigDecimal adjustmentFactor,
        BigDecimal maxPerCapitaIncome,
        Integer ageMin,
        Integer ageMax,
        String eligibilityCode,
        String creationLaw,
        LocalDate startedAt) {
}
