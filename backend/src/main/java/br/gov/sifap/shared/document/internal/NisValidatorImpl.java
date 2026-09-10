package br.gov.sifap.shared.document.internal;

import br.gov.sifap.shared.document.ValidationFailure;
import br.gov.sifap.shared.document.ValidationResult;

/**
 * Validacao de NIS por modulo 11 com pesos fixos.
 *
 * <p>Cobre {@code REQ-DOC-005} e {@code REQ-DOC-006}.
 * Origem legada: {@code SUBVALNI.NSN:43-44} e {@code SUBVALNI.NSN:64-150}.
 */
public final class NisValidatorImpl {

    public static final int LENGTH = 11;

    private static final int[] CHECK_DIGIT_WEIGHTS = {3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private static final int CHECK_DIGIT_POSITION = 10;

    private NisValidatorImpl() {
    }

    public static ValidationResult validate(String rawNis) {
        if (rawNis == null || rawNis.isBlank()) {
            return ValidationResult.fail(ValidationFailure.NAO_INFORMADO);
        }

        String nis = rawNis.trim();

        if (nis.length() != LENGTH || !Digits.isAllAsciiDigits(nis)) {
            return ValidationResult.fail(ValidationFailure.CARACTERE_INVALIDO);
        }

        if (Digits.isAllZeros(nis)) {
            return ValidationResult.fail(ValidationFailure.NAO_INFORMADO);
        }

        if (nis.charAt(CHECK_DIGIT_POSITION) - '0'
                != Modulo11Validator.checkDigit(nis, CHECK_DIGIT_WEIGHTS)) {
            return ValidationResult.fail(ValidationFailure.DIGITO_VERIFICADOR_INVALIDO);
        }

        return ValidationResult.ok();
    }
}
