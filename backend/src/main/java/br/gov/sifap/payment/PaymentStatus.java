package br.gov.sifap.payment;

import java.util.Optional;

/** Situacao do pagamento. Dominio de {@code PAYMENT.ddm:52-54}. */
public enum PaymentStatus {

    PENDENTE('P'),
    GERADO('G'),
    EMITIDO('E'),
    CONFIRMADO('C'),
    DEVOLVIDO('D'),
    CANCELADO('X'),
    REPROCESSADO('R');

    private final char legacyCode;

    PaymentStatus(char legacyCode) {
        this.legacyCode = legacyCode;
    }

    public char legacyCode() {
        return legacyCode;
    }

    public static Optional<PaymentStatus> fromLegacyCode(String code) {
        if (code == null || code.isBlank() || code.trim().length() != 1) {
            return Optional.empty();
        }
        char value = Character.toUpperCase(code.trim().charAt(0));
        for (PaymentStatus status : values()) {
            if (status.legacyCode == value) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }

    /**
     * Situacao que admite retorno do banco.
     *
     * <p>Atende {@code REQ-REC-011}. Somente o pagamento remetido pode receber retorno:
     * {@code BATCHCON.NSP:205-213} grava a nova situacao sem consultar a anterior, de modo
     * que um retorno atrasado sobrescreve um cancelamento.
     */
    public boolean acceptsBankReturn() {
        return this == EMITIDO;
    }
}
