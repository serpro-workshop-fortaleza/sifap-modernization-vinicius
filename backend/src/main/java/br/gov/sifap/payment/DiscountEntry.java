package br.gov.sifap.payment;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Lancamento de desconto sobre um pagamento.
 *
 * @param caseNumber processo judicial; obrigatorio no desconto {@code JUDICIAL}
 */
public record DiscountEntry(
        DiscountType type,
        BigDecimal amount,
        BigDecimal percentage,
        Optional<String> caseNumber) {

    public DiscountEntry {
        if (type == DiscountType.JUDICIAL && caseNumber.isEmpty()) {
            throw new IllegalArgumentException("desconto judicial exige numero de processo");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("valor de desconto invalido");
        }
    }

    public static DiscountEntry of(DiscountType type, BigDecimal amount) {
        return new DiscountEntry(type, amount, null, Optional.empty());
    }
}
