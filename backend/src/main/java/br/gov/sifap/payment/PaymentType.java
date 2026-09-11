package br.gov.sifap.payment;

import java.util.Optional;

/**
 * Natureza do pagamento.
 *
 * <p>Atende {@code REQ-PAY-016}. Dominio de {@code PAYMENT.ddm:74-75}.
 *
 * <p>{@code CALCBENF.NSN:273} grava {@code D} em dezembro — valor fora deste dominio, que
 * nenhum consumidor sabe interpretar. O pagamento de dezembro e {@code NORMAL}: o decimo
 * terceiro e o abono sao parcelas dele, com valores em campos proprios.
 */
public enum PaymentType {

    NORMAL('N'),
    RETROATIVO('R'),
    ABONO('A'),
    CORRECAO('C');

    private final char legacyCode;

    PaymentType(char legacyCode) {
        this.legacyCode = legacyCode;
    }

    public char legacyCode() {
        return legacyCode;
    }

    public static Optional<PaymentType> fromLegacyCode(String code) {
        if (code == null || code.isBlank() || code.trim().length() != 1) {
            return Optional.empty();
        }
        char value = Character.toUpperCase(code.trim().charAt(0));
        for (PaymentType type : values()) {
            if (type.legacyCode == value) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
