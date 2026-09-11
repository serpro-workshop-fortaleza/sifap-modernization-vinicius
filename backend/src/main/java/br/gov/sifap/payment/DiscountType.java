package br.gov.sifap.payment;

import java.util.Optional;

/**
 * Tipos de desconto aplicaveis.
 *
 * <p>Atende {@code REQ-PAY-018}. Dominio declarado em {@code PAYMENT.ddm:44}.
 *
 * <p>O legado move o campo {@code A 3} do dicionario para {@code #TYPE-DISC (A1)} e compara
 * com cinco letras ({@code CALCDSCT.NSP:45}, {@code :131-162}). O truncamento faz
 * {@code JD}, {@code PA} e {@code IR} funcionarem por acidente; {@code CS}, {@code EM},
 * {@code TX}, {@code OU} e {@code EX} caem em {@code NONE → IGNORE} e o desconto
 * desaparece em silencio.
 */
public enum DiscountType {

    IRRF("IR", false),
    /** Unico sem teto, conforme {@code CALCDSCT.NSP:132}. */
    JUDICIAL("JD", true),
    CONSIGNADO("CS", false),
    PENSAO("PA", false),
    EMPRESTIMO("EM", false),
    TAXA("TX", false),
    OUTRO("OU", false),
    EXTRAORDINARIO("EX", false);

    private final String legacyCode;
    private final String description;

    DiscountType(String legacyCode, boolean exemptFromCap) {
        this.legacyCode = legacyCode;
        this.description = exemptFromCap ? "isento do teto" : "sujeito ao teto";
    }

    public String legacyCode() {
        return legacyCode;
    }

    public boolean isExemptFromCap() {
        return this == JUDICIAL;
    }

    public String capDescription() {
        return description;
    }

    public static Optional<DiscountType> fromLegacyCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String value = code.trim().toUpperCase();
        for (DiscountType type : values()) {
            if (type.legacyCode.equals(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
