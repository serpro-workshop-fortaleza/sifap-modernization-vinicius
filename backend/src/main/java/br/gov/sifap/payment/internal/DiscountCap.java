package br.gov.sifap.payment.internal;

import br.gov.sifap.payment.DiscountType;
import br.gov.sifap.payment.internal.calculation.MonetaryScale;
import java.math.BigDecimal;
import java.util.List;

/**
 * Aplicacao do teto de desconto.
 *
 * <p>Atende {@code REQ-PAY-019}. O legado corta {@code #AMT-TOTAL-DISC}, que acumula
 * <strong>todos</strong> os tipos, dentro do laco ({@code CALCDSCT.NSP:170-175}). Quando um
 * desconto comum e processado depois de um judicial, o corte remove parte do judicial — que
 * o proprio programa declara nao ter teto em {@code :132}.
 *
 * <p>Aqui o judicial e somado fora do teto, e o resultado deixa de depender da ordem.
 */
final class DiscountCap {

    static final BigDecimal CAP_RATE = new BigDecimal("0.30");

    private DiscountCap() {
    }

    static BigDecimal apply(List<PaymentDiscount> discounts, BigDecimal grossAmount) {
        BigDecimal exempt = sumOf(discounts, true);
        BigDecimal capped = sumOf(discounts, false);
        BigDecimal ceiling = MonetaryScale.truncate(grossAmount.multiply(CAP_RATE));

        return MonetaryScale.truncate(exempt.add(capped.min(ceiling)));
    }

    private static BigDecimal sumOf(List<PaymentDiscount> discounts, boolean exemptFromCap) {
        return discounts.stream()
                .filter(discount -> discount.isExemptFromCap() == exemptFromCap)
                .map(PaymentDiscount::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static boolean isExempt(DiscountType type) {
        return type.isExemptFromCap();
    }
}
