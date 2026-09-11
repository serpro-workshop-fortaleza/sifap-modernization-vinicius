package br.gov.sifap.payment;

import java.util.Optional;

/**
 * Situacao da conferencia entre o valor pago e o valor confirmado pelo banco.
 *
 * <p>Atende {@code REQ-REC-009}. O dominio nao foi decidido aqui: {@code PAYMENT.ddm:95}
 * declara {@code GB STAT-RECONCIL A 1 F C=RECONCILED D=DIVERGENT} desde sempre, e nenhum
 * programa do acervo grava o campo.
 *
 * <p>E ortogonal a {@link PaymentStatus}: um pagamento divergente preserva a situacao que
 * tinha. Sao duas dimensoes, e o legado so tinha uma.
 */
public enum ReconciliationStatus {

    CONCILIADO('C'),
    DIVERGENTE('D');

    private final char legacyCode;

    ReconciliationStatus(char legacyCode) {
        this.legacyCode = legacyCode;
    }

    public char legacyCode() {
        return legacyCode;
    }

    public static Optional<ReconciliationStatus> fromLegacyCode(String code) {
        if (code == null || code.isBlank() || code.trim().length() != 1) {
            return Optional.empty();
        }
        char value = code.trim().charAt(0);
        for (ReconciliationStatus status : values()) {
            if (status.legacyCode == value) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
