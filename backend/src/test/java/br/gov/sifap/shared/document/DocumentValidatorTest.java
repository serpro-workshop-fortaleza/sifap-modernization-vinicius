package br.gov.sifap.shared.document;

import static org.assertj.core.api.Assertions.assertThat;

import br.gov.sifap.shared.document.internal.DefaultDocumentValidator;
import java.lang.reflect.Field;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T-008 — cobre {@code REQ-DOC-007}. */
class DocumentValidatorTest {

    private final DocumentValidator validator = new DefaultDocumentValidator();

    @Test
    @DisplayName("deve nao manter estado quando a fachada e chamada em sequencia")
    void deve_nao_manter_estado_quando_a_fachada_e_chamada_em_sequencia() {
        // AC-007.1: o kernel e funcao pura. Sem campos de instancia nao ha estado a vazar
        // entre chamadas, que e a condicao para consumo concorrente sem sincronizacao.
        Field[] fields = DefaultDocumentValidator.class.getDeclaredFields();

        assertThat(fields).isEmpty();
    }

    @Test
    @DisplayName("deve devolver o mesmo resultado quando a mesma entrada e repetida")
    void deve_devolver_o_mesmo_resultado_quando_a_mesma_entrada_e_repetida() {
        ValidationResult expected = validator.validateCpf("11144477735");

        assertThat(IntStream.range(0, 100)
                        .mapToObj(i -> validator.validateCpf("11144477735"))
                        .distinct()
                        .count())
                .isOne();
        assertThat(validator.validateCpf("11144477735")).isEqualTo(expected);
    }

    @Test
    @DisplayName("deve indicar o motivo especifico quando o documento e recusado")
    void deve_indicar_o_motivo_especifico_quando_o_documento_e_recusado() {
        // AC-007.2
        assertThat(validator.validateCpf("1234567890A").failure())
                .contains(ValidationFailure.CARACTERE_INVALIDO);
        assertThat(validator.validateNis("00000000000").failure())
                .contains(ValidationFailure.NAO_INFORMADO);
    }

    @Test
    @DisplayName("deve concordar com o value object quando o mesmo documento e validado")
    void deve_concordar_com_o_value_object_quando_o_mesmo_documento_e_validado() {
        assertThat(validator.validateCpf("11144477735").valid())
                .isEqualTo(Cpf.tryParse("11144477735").isPresent());
        assertThat(validator.validateNis("12345678919").valid())
                .isEqualTo(Nis.tryParse("12345678919").isPresent());
    }
}
