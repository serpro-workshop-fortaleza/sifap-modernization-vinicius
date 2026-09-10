package br.gov.sifap.shared.document.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.gov.sifap.shared.document.ValidationFailure;
import br.gov.sifap.shared.document.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T-003 e T-004 — cobre {@code REQ-DOC-001} a {@code REQ-DOC-004}.
 *
 * <p>Os vetores derivam da leitura de {@code CCVALCPF.NSC}, nao de execucao do SIFAP legado.
 * A limitacao esta registrada no plano e vale como premissa, nao como prova.
 */
class CpfValidatorImplTest {

    @ParameterizedTest(name = "CPF \"{0}\" e valido")
    @ValueSource(strings = {"11144477735", "12345678909"})
    @DisplayName("deve aceitar quando ambos os digitos verificadores conferem")
    void deve_aceitar_quando_ambos_os_digitos_verificadores_conferem(String cpf) {
        // REQ-DOC-003 / AC-003.3
        assertThat(CpfValidatorImpl.validate(cpf).valid()).isTrue();
    }

    @ParameterizedTest(name = "CPF \"{0}\" e recusado por caractere")
    @ValueSource(strings = {
        "1234567890A",  // AC-001.1
        "123.456.789-09",
        "1234567890",   // curto: equivale a campo A11 com brancos
        "123456789012"
    })
    @DisplayName("deve recusar quando ha caractere nao numerico ou comprimento diferente de onze")
    void deve_recusar_quando_ha_caractere_nao_numerico(String cpf) {
        // REQ-DOC-001
        assertThat(CpfValidatorImpl.validate(cpf).failure())
                .contains(ValidationFailure.CARACTERE_INVALIDO);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "           "})
    @DisplayName("deve recusar como nao informado quando o CPF esta ausente")
    void deve_recusar_como_nao_informado_quando_o_cpf_esta_ausente(String cpf) {
        // REQ-DOC-004 / AC-004.1
        assertThat(CpfValidatorImpl.validate(cpf).failure())
                .contains(ValidationFailure.NAO_INFORMADO);
    }

    @Test
    @DisplayName("deve distinguir zeros de digitos repetidos quando o CPF e composto so por zeros")
    void deve_distinguir_zeros_de_digitos_repetidos() {
        // REQ-DOC-004 / AC-004.2: o legado colapsa os dois casos no codigo 1001.
        assertThat(CpfValidatorImpl.validate("00000000000").failure())
                .contains(ValidationFailure.NAO_INFORMADO);
        assertThat(CpfValidatorImpl.validate("11111111111").failure())
                .contains(ValidationFailure.DIGITOS_REPETIDOS);
    }

    @ParameterizedTest(name = "CPF \"{0}\" e recusado por digitos repetidos")
    @ValueSource(strings = {
        "11111111111", "22222222222", "33333333333", "44444444444", "55555555555",
        "66666666666", "77777777777", "88888888888", "99999999999"
    })
    @DisplayName("deve recusar quando todos os digitos sao iguais")
    void deve_recusar_quando_todos_os_digitos_sao_iguais(String cpf) {
        // REQ-DOC-002 / AC-002.1
        assertThat(CpfValidatorImpl.validate(cpf).failure())
                .contains(ValidationFailure.DIGITOS_REPETIDOS);
    }

    @ParameterizedTest(name = "CPF \"{0}\" tem {1} incorreto")
    @CsvSource({
        "11144477745, primeiro",
        "12345678999, primeiro",
        "11144477734, segundo",
        "12345678908, segundo"
    })
    @DisplayName("deve recusar quando um digito verificador diverge do calculado")
    void deve_recusar_quando_um_digito_verificador_diverge(String cpf, String position) {
        // REQ-DOC-003 / AC-003.1 e AC-003.2
        assertThat(CpfValidatorImpl.validate(cpf).failure())
                .as("digito %s de %s", position, cpf)
                .contains(ValidationFailure.DIGITO_VERIFICADOR_INVALIDO);
    }

    @Test
    @DisplayName("deve tolerar espacos de borda quando o CPF vem de campo de tamanho fixo")
    void deve_tolerar_espacos_de_borda() {
        assertThat(CpfValidatorImpl.validate("  11144477735  ").valid()).isTrue();
    }

    @Test
    @DisplayName("deve devolver resultado sem efeito colateral quando chamado repetidamente")
    void deve_devolver_resultado_sem_efeito_colateral() {
        // REQ-DOC-007 / AC-007.1
        ValidationResult first = CpfValidatorImpl.validate("11144477735");
        CpfValidatorImpl.validate("00000000000");
        ValidationResult second = CpfValidatorImpl.validate("11144477735");

        assertThat(second).isEqualTo(first);
    }
}
