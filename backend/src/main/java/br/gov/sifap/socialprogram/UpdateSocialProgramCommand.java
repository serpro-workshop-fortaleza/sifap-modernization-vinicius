package br.gov.sifap.socialprogram;

import java.math.BigDecimal;

/**
 * Dados de alteracao de programa social.
 *
 * <p>Atende {@code REQ-PRG-006}, operacao que o legado nao possui: o {@code CADPROG}
 * implementa apenas inclusao e consulta ({@code CADPROG.NSP:80-83}).
 *
 * <p>Como na Fatia 2, <strong>nao existe campo de situacao</strong>. Mudar situacao e
 * operacao propria, com motivo.
 */
public record UpdateSocialProgramCommand(
        String name,
        String acronym,
        BigDecimal amountBase,
        BigDecimal adjustmentFactor,
        BigDecimal maxPerCapitaIncome,
        Integer ageMin,
        Integer ageMax,
        String eligibilityCode) {
}
