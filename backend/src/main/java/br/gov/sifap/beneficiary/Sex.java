package br.gov.sifap.beneficiary;

import java.util.Optional;

/**
 * Sexo declarado do beneficiario.
 *
 * <p>{@code BENEFIC.ddm:45} admite {@code I} para indefinido e
 * {@code CADBENEF.NSP:182-186} recusa qualquer valor diferente de {@code M} ou {@code F}.
 * O cadastro preserva a restricao do programa ({@code REQ-BEN-002}); a carga aceita
 * {@code I}, porque ele pode existir na base.
 */
public enum Sex {

    M,
    F,
    I;

    /** Valores aceitos no cadastro, conforme o comportamento do programa legado. */
    public boolean acceptedOnRegistration() {
        return this != I;
    }

    public static Optional<Sex> fromLegacyCode(String code) {
        if (code == null || code.isBlank() || code.trim().length() != 1) {
            return Optional.empty();
        }
        return switch (code.trim().toUpperCase()) {
            case "M" -> Optional.of(M);
            case "F" -> Optional.of(F);
            case "I" -> Optional.of(I);
            default -> Optional.empty();
        };
    }
}
