package br.gov.sifap.shared.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T-002 — cobre {@code REQ-DOC-004} e {@code REQ-DOC-007}. */
class ValidationResultTest {

    @Test
    @DisplayName("deve nao carregar motivo quando o resultado e valido")
    void deve_nao_carregar_motivo_quando_o_resultado_e_valido() {
        ValidationResult result = ValidationResult.ok();

        assertThat(result.valid()).isTrue();
        assertThat(result.invalid()).isFalse();
        assertThat(result.failure()).isEmpty();
    }

    @Test
    @DisplayName("deve carregar motivo quando o resultado e invalido")
    void deve_carregar_motivo_quando_o_resultado_e_invalido() {
        ValidationResult result = ValidationResult.fail(ValidationFailure.NAO_INFORMADO);

        assertThat(result.valid()).isFalse();
        assertThat(result.invalid()).isTrue();
        assertThat(result.failure()).contains(ValidationFailure.NAO_INFORMADO);
    }

    @Test
    @DisplayName("deve recusar a construcao quando valido e invalido sao combinados com motivo incoerente")
    void deve_recusar_a_construcao_quando_valido_e_motivo_sao_incoerentes() {
        assertThatThrownBy(() -> new ValidationResult(true, Optional.of(ValidationFailure.NAO_INFORMADO)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ValidationResult(false, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deve expor os quatro motivos da especificacao sem valor generico de reserva")
    void deve_expor_os_quatro_motivos_da_especificacao() {
        // REQ-DOC-004: o contrato legado declara 1003 para digitos repetidos e nunca o emite.
        // Um enum exaustivo impede que o sistema novo repita a lacuna.
        assertThat(ValidationFailure.values())
                .containsExactlyInAnyOrder(
                        ValidationFailure.NAO_INFORMADO,
                        ValidationFailure.CARACTERE_INVALIDO,
                        ValidationFailure.DIGITOS_REPETIDOS,
                        ValidationFailure.DIGITO_VERIFICADOR_INVALIDO);
    }

    @Test
    @DisplayName("deve descrever cada motivo em texto proprio")
    void deve_descrever_cada_motivo_em_texto_proprio() {
        assertThat(ValidationFailure.values())
                .extracting(ValidationFailure::description)
                .doesNotHaveDuplicates()
                .allSatisfy(description -> assertThat(description).isNotBlank());
    }
}
