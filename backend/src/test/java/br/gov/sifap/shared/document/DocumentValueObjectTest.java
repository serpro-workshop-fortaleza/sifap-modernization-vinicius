package br.gov.sifap.shared.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** T-007 — cobre {@code REQ-DOC-007}. */
class DocumentValueObjectTest {

    private static final String VALID_CPF = "11144477735";
    private static final String VALID_NIS = "12345678919";

    @Test
    @DisplayName("deve criar o CPF quando o documento e valido")
    void deve_criar_o_cpf_quando_o_documento_e_valido() {
        assertThat(Cpf.of(VALID_CPF).value()).isEqualTo(VALID_CPF);
    }

    @ParameterizedTest(name = "CPF \"{0}\" nao produz instancia")
    @ValueSource(strings = {"11111111111", "00000000000", "1234567890A", "12345678908"})
    @DisplayName("deve lancar excecao quando of recebe CPF invalido")
    void deve_lancar_excecao_quando_of_recebe_cpf_invalido(String cpf) {
        assertThatThrownBy(() -> Cpf.of(cpf)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deve omitir o documento quando a excecao e construida")
    void deve_omitir_o_documento_quando_a_excecao_e_construida() {
        // O CPF e dado pessoal: a mensagem carrega o motivo, nunca o valor.
        assertThatThrownBy(() -> Cpf.of("11111111111"))
                .hasMessageNotContaining("11111111111")
                .hasMessageContaining("digitos repetidos");
    }

    @Test
    @DisplayName("deve devolver Optional vazio quando tryParse recebe CPF invalido")
    void deve_devolver_optional_vazio_quando_tryparse_recebe_cpf_invalido() {
        assertThat(Cpf.tryParse("11111111111")).isEmpty();
        assertThat(Cpf.tryParse(null)).isEmpty();
        assertThat(Cpf.tryParse(VALID_CPF)).isPresent();
    }

    @Test
    @DisplayName("deve mascarar o documento quando o CPF e convertido em texto")
    void deve_mascarar_o_documento_quando_o_cpf_e_convertido_em_texto() {
        Cpf cpf = Cpf.of(VALID_CPF);

        assertThat(cpf.toString()).isEqualTo("***.444.777-**").doesNotContain(VALID_CPF);
        assertThat(cpf.masked()).isEqualTo(cpf.toString());
    }

    @Test
    @DisplayName("deve comparar CPFs pelo valor")
    void deve_comparar_cpfs_pelo_valor() {
        assertThat(Cpf.of(VALID_CPF))
                .isEqualTo(Cpf.of(VALID_CPF))
                .hasSameHashCodeAs(Cpf.of(VALID_CPF))
                .isNotEqualTo(Cpf.of("12345678909"));
    }

    @Test
    @DisplayName("deve criar o NIS quando o documento e valido")
    void deve_criar_o_nis_quando_o_documento_e_valido() {
        assertThat(Nis.of(VALID_NIS).value()).isEqualTo(VALID_NIS);
        assertThat(Nis.of(VALID_NIS)).isEqualTo(Nis.of(VALID_NIS));
        assertThat(Nis.of(VALID_NIS).toString()).doesNotContain(VALID_NIS);
    }

    @Test
    @DisplayName("deve recusar o NIS quando o digito verificador diverge")
    void deve_recusar_o_nis_quando_o_digito_verificador_diverge() {
        assertThatThrownBy(() -> Nis.of("12345678918"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Nis.tryParse("12345678918")).isEmpty();
    }

    @Test
    @DisplayName("deve impedir a criacao por construtor quando o tipo e um value object de documento")
    void deve_impedir_a_criacao_por_construtor() {
        // Esta e a garantia que elimina o antipadrao de CADBENEF.NSP:161 contra :164 —
        // chamar a validacao e prosseguir com o documento mesmo assim.
        assertThat(publicConstructorsOf(Cpf.class)).isZero();
        assertThat(publicConstructorsOf(Nis.class)).isZero();
    }

    private static long publicConstructorsOf(Class<?> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        return java.util.Arrays.stream(constructors)
                .filter(c -> Modifier.isPublic(c.getModifiers()))
                .count();
    }
}
