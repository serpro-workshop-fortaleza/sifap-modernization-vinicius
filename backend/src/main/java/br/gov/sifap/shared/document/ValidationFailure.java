package br.gov.sifap.shared.document;

/**
 * Motivos pelos quais um documento e recusado.
 *
 * <p>O conjunto e exaustivo por decisao de projeto. O contrato legado
 * ({@code PDAVALID.NSA:31-40}) declara o codigo {@code 1003} para digitos repetidos
 * e nunca o emite, devolvendo {@code 1001} nos dois casos. Um enum sem valor generico
 * de reserva impede repetir essa lacuna: um motivo novo exige alteracao compilada.
 *
 * <p>Atende {@code REQ-DOC-004}.
 */
public enum ValidationFailure {

    /** Documento em branco, ausente ou composto apenas por zeros. */
    NAO_INFORMADO("documento nao informado"),

    /** Documento com caractere nao numerico ou com comprimento diferente do esperado. */
    CARACTERE_INVALIDO("caractere nao numerico"),

    /** Todos os digitos iguais entre si. */
    DIGITOS_REPETIDOS("digitos repetidos"),

    /** Digito verificador diferente do calculado por modulo 11. */
    DIGITO_VERIFICADOR_INVALIDO("digito verificador invalido");

    private final String description;

    ValidationFailure(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
