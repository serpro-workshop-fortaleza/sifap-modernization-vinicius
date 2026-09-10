package br.gov.sifap.shared.document.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.gov.sifap.shared.document.ValidationFailure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** T-006 — cobre {@code REQ-DOC-005} e {@code REQ-DOC-006}. */
class NisValidatorImplTest {

    @ParameterizedTest(name = "NIS \"{0}\" e valido")
    @ValueSource(strings = {"12345678919", "10000000008"})
    @DisplayName("deve aceitar quando o digito verificador confere")
    void deve_aceitar_quando_o_digito_verificador_confere(String nis) {
        // REQ-DOC-005 / AC-005.2
        assertThat(NisValidatorImpl.validate(nis).valid()).isTrue();
    }

    @ParameterizedTest(name = "NIS \"{0}\" e invalido")
    @ValueSource(strings = {"12345678918", "10000000000"})
    @DisplayName("deve recusar quando o digito verificador diverge do calculado")
    void deve_recusar_quando_o_digito_verificador_diverge(String nis) {
        // REQ-DOC-005 / AC-005.1
        assertThat(NisValidatorImpl.validate(nis).failure())
                .contains(ValidationFailure.DIGITO_VERIFICADOR_INVALIDO);
    }

    @Test
    @DisplayName("deve recusar como nao informado quando o NIS e composto so por zeros")
    void deve_recusar_como_nao_informado_quando_o_nis_e_composto_so_por_zeros() {
        // REQ-DOC-006 / AC-006.1
        assertThat(NisValidatorImpl.validate("00000000000").failure())
                .contains(ValidationFailure.NAO_INFORMADO);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("deve recusar como nao informado quando o NIS esta ausente")
    void deve_recusar_como_nao_informado_quando_o_nis_esta_ausente(String nis) {
        // REQ-DOC-006
        assertThat(NisValidatorImpl.validate(nis).failure())
                .contains(ValidationFailure.NAO_INFORMADO);
    }

    @ParameterizedTest(name = "NIS \"{0}\" e recusado por caractere")
    @ValueSource(strings = {"1234567890X", "123.45678.91-9", "1234567891"})
    @DisplayName("deve recusar quando ha caractere nao numerico ou comprimento diferente de onze")
    void deve_recusar_quando_ha_caractere_nao_numerico(String nis) {
        // REQ-DOC-006 / AC-006.2
        assertThat(NisValidatorImpl.validate(nis).failure())
                .contains(ValidationFailure.CARACTERE_INVALIDO);
    }
}
