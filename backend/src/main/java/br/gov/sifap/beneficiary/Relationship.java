package br.gov.sifap.beneficiary;

import java.util.Optional;

/**
 * Grau de parentesco do dependente.
 *
 * <p>O dominio e a <strong>uniao</strong> de duas declaracoes que quase nao se
 * sobrepoem:
 *
 * <ul>
 *   <li>{@code BENEFIC.ddm:91-92} declara {@code FI}, {@code CJ}, {@code NT} e {@code TU}
 *   <li>{@code CADDEPEN.NSP:152-156} aceita {@code FI}, {@code CO}, {@code IR} e {@code OU}
 * </ul>
 *
 * <p>Apenas {@code FI} coincide. Escolher um dos dois dominios descartaria dado real, e
 * nao ha como saber qual predomina sem medir: e o que o {@code REQ-BEN-021} faz na carga.
 *
 * <p>{@link #CJ} e {@link #CO} designam a mesma relacao — o dicionario grafa
 * {@code CJ=SPOUSE} e o programa grafa {@code CO=SPOUSE}. A unificacao depende do
 * inventario, e por isso os dois convivem aqui.
 */
public enum Relationship {

    FI("FI", "filho"),
    CJ("CJ", "conjuge"),
    NT("NT", "neto"),
    TU("TU", "tutelado"),
    CO("CO", "conjuge"),
    IR("IR", "irmao"),
    OU("OU", "outro");

    private final String legacyCode;
    private final String description;

    Relationship(String legacyCode, String description) {
        this.legacyCode = legacyCode;
        this.description = description;
    }

    public String legacyCode() {
        return legacyCode;
    }

    public String description() {
        return description;
    }

    /** Verdadeiro para os valores que o dicionario declara e o programa nao aceita. */
    public boolean declaredOnlyInDictionary() {
        return this == CJ || this == NT || this == TU;
    }

    public static Optional<Relationship> fromLegacyCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String value = code.trim().toUpperCase();
        for (Relationship relationship : values()) {
            if (relationship.legacyCode.equals(value)) {
                return Optional.of(relationship);
            }
        }
        return Optional.empty();
    }
}
