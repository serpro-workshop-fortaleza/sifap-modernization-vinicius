package br.gov.sifap.shared.document.internal;

import br.gov.sifap.shared.document.ValidationFailure;
import br.gov.sifap.shared.document.ValidationResult;

/**
 * Validacao de CPF unificada, equivalente a variante normativa {@code CCVALCPF.NSC}.
 *
 * <p>Cobre {@code REQ-DOC-001}, {@code REQ-DOC-002}, {@code REQ-DOC-003} e {@code REQ-DOC-004}.
 * A unificacao das cinco implementacoes legadas divergentes esta decidida no ADR-0005.
 *
 * <p>Nao existe tratamento de prefixo especial nem de documento de teste:
 * {@code REQ-DOC-008} e {@code REQ-DOC-009} sao atendidos pela ausencia dessas excecoes.
 */
public final class CpfValidatorImpl {

    public static final int LENGTH = 11;

    private static final int[] FIRST_CHECK_DIGIT_WEIGHTS = {10, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_CHECK_DIGIT_WEIGHTS = {11, 10, 9, 8, 7, 6, 5, 4, 3, 2};

    private static final int FIRST_CHECK_DIGIT_POSITION = 9;
    private static final int SECOND_CHECK_DIGIT_POSITION = 10;

    private CpfValidatorImpl() {
    }

    public static ValidationResult validate(String rawCpf) {
        if (rawCpf == null || rawCpf.isBlank()) {
            return ValidationResult.fail(ValidationFailure.NAO_INFORMADO);
        }

        String cpf = rawCpf.trim();

        // Comprimento diferente de 11 equivale a um campo A11 legado preenchido com brancos,
        // que a rotina normativa recusa na verificacao de caractere.
        if (cpf.length() != LENGTH || !Digits.isAllAsciiDigits(cpf)) {
            return ValidationResult.fail(ValidationFailure.CARACTERE_INVALIDO);
        }

        // Ordem exigida por REQ-DOC-004: zeros sao ausencia de documento, nao repeticao.
        if (Digits.isAllZeros(cpf)) {
            return ValidationResult.fail(ValidationFailure.NAO_INFORMADO);
        }

        if (Digits.isAllSameDigit(cpf)) {
            return ValidationResult.fail(ValidationFailure.DIGITOS_REPETIDOS);
        }

        if (digitAt(cpf, FIRST_CHECK_DIGIT_POSITION)
                != Modulo11Validator.checkDigit(cpf, FIRST_CHECK_DIGIT_WEIGHTS)) {
            return ValidationResult.fail(ValidationFailure.DIGITO_VERIFICADOR_INVALIDO);
        }

        if (digitAt(cpf, SECOND_CHECK_DIGIT_POSITION)
                != Modulo11Validator.checkDigit(cpf, SECOND_CHECK_DIGIT_WEIGHTS)) {
            return ValidationResult.fail(ValidationFailure.DIGITO_VERIFICADOR_INVALIDO);
        }

        return ValidationResult.ok();
    }

    private static int digitAt(String cpf, int position) {
        return cpf.charAt(position) - '0';
    }
}
