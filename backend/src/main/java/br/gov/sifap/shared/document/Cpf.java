package br.gov.sifap.shared.document;

import br.gov.sifap.shared.document.internal.CpfValidatorImpl;
import java.util.Objects;
import java.util.Optional;

/**
 * CPF valido.
 *
 * <p>Nao existe construtor publico: uma instancia so e obtida por {@link #of(String)}
 * ou {@link #tryParse(String)}, ambas validantes. Um metodo que recebe {@code Cpf}
 * tem a garantia de que o documento passou pela regra unificada e nao precisa revalidar.
 *
 * <p>E esta garantia que elimina o antipadrao central do legado, no qual
 * {@code CADBENEF.NSP:161} chama a validacao e {@code :164} prossegue ignorando o retorno.
 *
 * <p>Atende {@code REQ-DOC-007}.
 */
public final class Cpf {

    private final String value;

    private Cpf(String value) {
        this.value = value;
    }

    /**
     * @throws IllegalArgumentException se o CPF for invalido
     */
    public static Cpf of(String rawCpf) {
        ValidationResult result = CpfValidatorImpl.validate(rawCpf);
        if (result.invalid()) {
            // A mensagem carrega o motivo, nunca o documento.
            throw new IllegalArgumentException(
                    "CPF invalido: " + result.failure().orElseThrow().description());
        }
        return new Cpf(rawCpf.trim());
    }

    public static Optional<Cpf> tryParse(String rawCpf) {
        return CpfValidatorImpl.validate(rawCpf).valid()
                ? Optional.of(new Cpf(rawCpf.trim()))
                : Optional.empty();
    }

    public String value() {
        return value;
    }

    /** Representacao para log e mensagem de erro, sem expor o documento completo. */
    public String masked() {
        return mask(value);
    }

    /**
     * Mascara um CPF sem exigir que ele seja valido.
     *
     * <p>Necessario para exibir registro migrado cujo documento e invalido: ocultar dado
     * pessoal nao pode depender de o dado estar correto.
     */
    public static String mask(String rawCpf) {
        if (rawCpf == null) {
            return "";
        }
        String digits = rawCpf.trim();
        if (digits.length() != CpfValidatorImpl.LENGTH) {
            return "***";
        }
        return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Cpf cpf && value.equals(cpf.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    /** Devolve a forma mascarada para que um log acidental nao exponha o documento. */
    @Override
    public String toString() {
        return masked();
    }
}
