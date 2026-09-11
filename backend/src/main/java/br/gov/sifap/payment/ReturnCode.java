package br.gov.sifap.payment;

import java.util.Optional;

/**
 * Codigo de retorno do arquivo bancario.
 *
 * <p>Atende {@code REQ-REC-007}. Os tres codigos vem de
 * {@code BATCHCON.NSP:204-236}, que os trata em um {@code DECIDE}.
 *
 * <p>{@link #fromCode(String)} devolve vazio para o desconhecido em vez de um valor
 * padrao. E o que torna possivel o {@code REQ-REC-008}: no legado o ramo {@code NONE}
 * apenas escreve no log, e o registro ja foi contado como conciliado linha acima.
 */
public enum ReturnCode {

    /** Credito efetuado. */
    CREDITADO("00"),
    /** Devolvido pelo banco. */
    DEVOLVIDO("01"),
    /** Estornado apos o credito. */
    ESTORNADO("02");

    private final String code;

    ReturnCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<ReturnCode> fromCode(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        for (ReturnCode returnCode : values()) {
            if (returnCode.code.equals(trimmed)) {
                return Optional.of(returnCode);
            }
        }
        return Optional.empty();
    }
}
