package br.gov.sifap.socialprogram;

import java.util.Optional;

/**
 * Natureza do programa social.
 *
 * <p>Dominio declarado em {@code SOCPROG.ddm:31-33}. O comentario da view em
 * {@code CADPROG.NSP:18} usa outras palavras para os mesmos tres codigos.
 */
public enum SocialProgramType {

    ASSISTENCIA('A'),
    TRABALHO('T'),
    PREVIDENCIA('P');

    private final char legacyCode;

    SocialProgramType(char legacyCode) {
        this.legacyCode = legacyCode;
    }

    public char legacyCode() {
        return legacyCode;
    }

    public static Optional<SocialProgramType> fromLegacyCode(String code) {
        return SocialProgramStatus.fromChar(code, values(), SocialProgramType::legacyCode);
    }
}
