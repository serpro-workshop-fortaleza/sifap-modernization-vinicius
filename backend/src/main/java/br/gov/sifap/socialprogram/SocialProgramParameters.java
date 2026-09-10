package br.gov.sifap.socialprogram;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Parametros consumidos pelo calculo do beneficio.
 *
 * <p>Atende {@code REQ-PRG-009}. E deliberadamente enxuta: exatamente o conjunto que
 * {@code VALELEG.NSN:106-111} e {@code CALCBENF.NSN:194-196} leem hoje.
 *
 * <p>Separar esta projecao da de administracao evita que a Fatia 4 dependa de campos que
 * nao usa — o acoplamento que o {@code REQ-BEN-019} da Fatia 2 criticou no legado.
 *
 * @param ageMin zero significa ausencia de limite, conforme {@code SOCPROG.ddm:57}
 * @param ageMax zero significa ausencia de limite
 */
public record SocialProgramParameters(
        String code,
        SocialProgramType type,
        SocialProgramStatus status,
        BigDecimal amountBase,
        BigDecimal adjustmentFactor,
        Optional<String> eligibilityCode,
        int ageMin,
        int ageMax,
        Optional<BigDecimal> maxPerCapitaIncome) {
}
