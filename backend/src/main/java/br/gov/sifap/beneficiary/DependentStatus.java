package br.gov.sifap.beneficiary;

import java.util.Optional;

/**
 * Situacao do dependente.
 *
 * <p>Atende {@code REQ-BEN-010}. Dominio declarado em {@code BENEFIC.ddm:93} e nunca
 * preenchido pelo legado: {@code CADDEPEN.NSP:194-202} grava o dependente sem tocar o
 * campo, de modo que todo dependente da base tem situacao em branco.
 */
public enum DependentStatus {

    ATIVO('A'),
    INATIVO('I'),
    DESLIGADO('D');

    private final char legacyCode;

    DependentStatus(char legacyCode) {
        this.legacyCode = legacyCode;
    }

    public char legacyCode() {
        return legacyCode;
    }

    public static Optional<DependentStatus> fromLegacyCode(String code) {
        if (code == null || code.isBlank() || code.trim().length() != 1) {
            return Optional.empty();
        }
        char value = code.trim().charAt(0);
        for (DependentStatus status : values()) {
            if (status.legacyCode == value) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
