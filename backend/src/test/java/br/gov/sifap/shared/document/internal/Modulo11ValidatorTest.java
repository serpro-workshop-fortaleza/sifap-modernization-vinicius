package br.gov.sifap.shared.document.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * T-001 — apoia {@code REQ-DOC-003} e {@code REQ-DOC-005}.
 *
 * <p>Os vetores exercitam explicitamente os restos 0, 1 e maior que 1, porque a regra
 * "resto menor que dois resulta em zero" e o unico ponto em que as duas rotinas legadas
 * concordam e cuja duplicacao originou as variantes divergentes.
 */
class Modulo11ValidatorTest {

    private static final int[] CPF_FIRST_WEIGHTS = {10, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] NIS_WEIGHTS = {3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    @ParameterizedTest(name = "pesos de CPF sobre \"{0}\" produzem digito {1}")
    @CsvSource({
        "000000000, 0",  // soma 0, resto 0
        "000000006, 0",  // soma 12, resto 1
        "000000001, 9",  // soma 2,  resto 2
        "000000002, 7",  // soma 4,  resto 4
        "111444777, 3"   // vetor do CPF 111.444.777-35
    })
    @DisplayName("deve calcular o digito quando os pesos de CPF sao aplicados")
    void deve_calcular_digito_quando_pesos_de_cpf_sao_aplicados(String digits, int expected) {
        assertThat(Modulo11Validator.checkDigit(digits, CPF_FIRST_WEIGHTS)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "pesos de NIS sobre \"{0}\" produzem digito {1}")
    @CsvSource({
        "0000000000, 0",  // soma 0, resto 0
        "0000000001, 9",  // soma 2, resto 2
        "1234567891, 9"   // vetor do NIS 12345678919
    })
    @DisplayName("deve calcular o digito quando os pesos de NIS sao aplicados")
    void deve_calcular_digito_quando_pesos_de_nis_sao_aplicados(String digits, int expected) {
        assertThat(Modulo11Validator.checkDigit(digits, NIS_WEIGHTS)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "resto {0} resulta em digito zero")
    @CsvSource({"000000000", "000000006"})
    @DisplayName("deve atribuir zero quando o resto e menor que dois")
    void deve_atribuir_zero_quando_o_resto_e_menor_que_dois(String digits) {
        assertThat(Modulo11Validator.checkDigit(digits, CPF_FIRST_WEIGHTS)).isZero();
    }
}
