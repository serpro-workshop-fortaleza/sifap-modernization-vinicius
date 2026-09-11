package br.gov.sifap.payment.internal.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Truncamento monetario.
 *
 * <p>Atende {@code REQ-PAY-014}. O legado trunca porque move o valor para
 * {@code #AMT-TEMP (N11)}, um campo inteiro, e divide de volta
 * ({@code CALCBENF.NSN:264-266}). O comportamento e correto e a causa e acidental.
 *
 * <p>Aqui e explicito e vive em um unico lugar, de modo que nenhum valor com escala maior
 * que duas casas alcance o banco.
 */
public final class MonetaryScale {

    public static final int SCALE = 2;

    private MonetaryScale() {
    }

    public static BigDecimal truncate(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.DOWN);
    }
}
