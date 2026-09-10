package br.gov.sifap.shared.document;

import br.gov.sifap.shared.document.internal.NisValidatorImpl;
import java.util.Objects;
import java.util.Optional;

/**
 * NIS valido.
 *
 * <p>Mesmas garantias de {@link Cpf}: sem construtor publico, criacao apenas por
 * fabrica validante. Atende {@code REQ-DOC-007}.
 */
public final class Nis {

    private final String value;

    private Nis(String value) {
        this.value = value;
    }

    /**
     * @throws IllegalArgumentException se o NIS for invalido
     */
    public static Nis of(String rawNis) {
        ValidationResult result = NisValidatorImpl.validate(rawNis);
        if (result.invalid()) {
            throw new IllegalArgumentException(
                    "NIS invalido: " + result.failure().orElseThrow().description());
        }
        return new Nis(rawNis.trim());
    }

    public static Optional<Nis> tryParse(String rawNis) {
        return NisValidatorImpl.validate(rawNis).valid()
                ? Optional.of(new Nis(rawNis.trim()))
                : Optional.empty();
    }

    public String value() {
        return value;
    }

    public String masked() {
        return "***" + value.substring(3, 9) + "**";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Nis nis && value.equals(nis.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return masked();
    }
}
