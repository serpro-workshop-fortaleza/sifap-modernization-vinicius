package br.gov.sifap.payment.internal.calculation;

import java.math.BigDecimal;

/**
 * Acrescimo por faixa etaria.
 *
 * <p>Preserva {@code CALCBENF.NSN:240-251}. A ordem dos testes e a do legado: 65 anos ou
 * mais tem precedencia sobre 60, e menores de 18 vem por ultimo.
 */
public final class AgeFactor {

    private static final BigDecimal ELDERLY = new BigDecimal("1.1500");
    private static final BigDecimal SENIOR = new BigDecimal("1.1000");
    private static final BigDecimal MINOR = new BigDecimal("1.0500");
    private static final BigDecimal STANDARD = new BigDecimal("1.0000");

    private AgeFactor() {
    }

    public static BigDecimal of(int age) {
        if (age >= 65) {
            return ELDERLY;
        }
        if (age >= 60) {
            return SENIOR;
        }
        if (age < 18) {
            return MINOR;
        }
        return STANDARD;
    }
}
