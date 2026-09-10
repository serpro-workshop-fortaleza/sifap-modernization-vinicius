package br.gov.sifap.socialprogram;

import java.util.Optional;

/**
 * Situacao do programa social.
 *
 * <p>Dominio declarado em {@code SOCPROG.ddm:37} desde 1997.
 * {@code CADPROG.NSP:134} grava {@code A} sempre, de modo que {@code I} e {@code E} nunca
 * foram atribuidos por nenhum programa do acervo.
 *
 * <p>A consequencia e que {@code VALELEG.NSN:114-118} implementa a recusa por programa
 * inativo — uma regra que existe e nunca e acionada.
 */
public enum SocialProgramStatus {

    ATIVO('A'),
    INATIVO('I'),
    ENCERRADO('E');

    private final char legacyCode;

    SocialProgramStatus(char legacyCode) {
        this.legacyCode = legacyCode;
    }

    public char legacyCode() {
        return legacyCode;
    }

    /**
     * Encerrado e terminal.
     *
     * <p>Diferente do beneficiario, aqui ha fonte para a maquina de estados:
     * {@code SOCPROG.ddm:36} declara {@code DT-CLOSURE} com {@code 0=ACTIVE}, o que
     * estabelece que um programa encerrado tem data de encerramento e a data nao se desfaz.
     */
    public boolean canTransitionTo(SocialProgramStatus target) {
        return this != ENCERRADO && this != target;
    }

    public static Optional<SocialProgramStatus> fromLegacyCode(String code) {
        return fromChar(code, values(), SocialProgramStatus::legacyCode);
    }

    static <E extends Enum<E>> Optional<E> fromChar(
            String code, E[] values, java.util.function.Function<E, Character> extractor) {
        if (code == null || code.isBlank() || code.trim().length() != 1) {
            return Optional.empty();
        }
        char value = Character.toUpperCase(code.trim().charAt(0));
        for (E candidate : values) {
            if (extractor.apply(candidate) == value) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
