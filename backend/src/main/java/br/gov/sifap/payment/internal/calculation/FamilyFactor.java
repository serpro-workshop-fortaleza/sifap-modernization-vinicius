package br.gov.sifap.payment.internal.calculation;

import java.math.BigDecimal;

/**
 * Acrescimo por dependente.
 *
 * <p>Preserva {@code CALCBENF.NSN:205-218}: 5% por dependente ate dois, 3% do terceiro ao
 * quarto, 2% do quinto em diante.
 */
public final class FamilyFactor {

    private static final BigDecimal FIRST_TIER_RATE = new BigDecimal("0.0500");
    private static final BigDecimal SECOND_TIER_RATE = new BigDecimal("0.0300");
    private static final BigDecimal THIRD_TIER_RATE = new BigDecimal("0.0200");

    private static final BigDecimal SECOND_TIER_BASE = new BigDecimal("1.1000");
    private static final BigDecimal THIRD_TIER_BASE = new BigDecimal("1.1600");

    private FamilyFactor() {
    }

    public static BigDecimal of(int activeDependents) {
        if (activeDependents <= 0) {
            return BigDecimal.ONE;
        }
        if (activeDependents <= 2) {
            return BigDecimal.ONE.add(FIRST_TIER_RATE.multiply(BigDecimal.valueOf(activeDependents)));
        }
        if (activeDependents <= 4) {
            return SECOND_TIER_BASE.add(
                    SECOND_TIER_RATE.multiply(BigDecimal.valueOf(activeDependents - 2L)));
        }
        return THIRD_TIER_BASE.add(THIRD_TIER_RATE.multiply(BigDecimal.valueOf(activeDependents - 4L)));
    }
}
