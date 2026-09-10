package br.gov.sifap.shared.document;

import java.util.Objects;
import java.util.Optional;

/**
 * Resultado de uma validacao de documento, devolvido como valor.
 *
 * <p>Atende {@code REQ-DOC-007}: a validacao nao grava dados, nao escreve em tela
 * e nao altera estado. Equivale ao retorno por PDA do legado
 * ({@code PDAVALID.NSA:8-10}), com motivos tipados no lugar de codigo numerico.
 *
 * <p>O motivo e {@link Optional} para que nenhum acessor publico devolva {@code null}.
 *
 * @param valid indica se o documento foi aceito
 * @param failure motivo da recusa; presente se e somente se {@code valid} for falso
 */
public record ValidationResult(boolean valid, Optional<ValidationFailure> failure) {

    private static final ValidationResult OK = new ValidationResult(true, Optional.empty());

    public ValidationResult {
        Objects.requireNonNull(failure, "failure");
        if (valid == failure.isPresent()) {
            throw new IllegalArgumentException(
                    "resultado valido nao carrega motivo e resultado invalido exige motivo");
        }
    }

    public static ValidationResult ok() {
        return OK;
    }

    public static ValidationResult fail(ValidationFailure reason) {
        return new ValidationResult(false, Optional.of(Objects.requireNonNull(reason, "reason")));
    }

    public boolean invalid() {
        return !valid;
    }
}
