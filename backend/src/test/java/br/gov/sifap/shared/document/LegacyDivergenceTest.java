package br.gov.sifap.shared.document;

import static org.assertj.core.api.Assertions.assertThat;

import br.gov.sifap.shared.document.internal.DefaultDocumentValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T-005 — divergencias deliberadas em relacao ao SIFAP legado.
 *
 * <p>Estes testes <strong>falham contra o legado por design</strong>. Cada um documenta um
 * requisito de nivel {@code C} do ADR-0003, e o comentario aponta o membro Natural cujo
 * comportamento deixa de ser reproduzido. Se a validacao humana dos misterios
 * {@code SIFAP-M-14}, {@code SIFAP-M-15} ou {@code SIFAP-M-16} contrariar a decisao,
 * e aqui que a reversao comeca.
 */
class LegacyDivergenceTest {

    private final DocumentValidator validator = new DefaultDocumentValidator();

    @Test
    @DisplayName("deve recusar CPF com digitos repetidos ainda que o legado aceite")
    void deve_recusar_cpf_com_digitos_repetidos_ainda_que_o_legado_aceite() {
        // REQ-DOC-002, nivel C. A copia embutida em CADBENEF.NSP:344-413 nao implementa
        // a verificacao de digitos repetidos, e 111.111.111-11 satisfaz o modulo 11.
        ValidationResult result = validator.validateCpf("11111111111");

        assertThat(result.invalid()).isTrue();
        assertThat(result.failure()).contains(ValidationFailure.DIGITOS_REPETIDOS);
    }

    @Test
    @DisplayName("deve recusar CPF de prefixo especial com digito verificador incorreto")
    void deve_recusar_cpf_de_prefixo_especial_com_digito_incorreto() {
        // REQ-DOC-008, nivel C. VALDOCS.NSP:226-241 zera todos os erros acumulados
        // quando reconhece um dos oito prefixos especiais.
        ValidationResult result = validator.validateCpf("99912345699");

        assertThat(result.invalid()).isTrue();
        assertThat(result.failure()).contains(ValidationFailure.DIGITO_VERIFICADOR_INVALIDO);
    }

    @Test
    @DisplayName("deve preservar erro anterior quando um documento de prefixo especial e validado depois")
    void deve_preservar_erro_anterior_quando_prefixo_especial_e_validado_depois() {
        // REQ-DOC-008 / AC-008.2. No legado o prefixo especial zera erros de outros
        // documentos do mesmo registro. Aqui cada validacao e independente por construcao.
        ValidationResult invalidFirst = validator.validateCpf("12345678900");
        validator.validateCpf("99912345600");

        assertThat(invalidFirst.invalid()).isTrue();
        assertThat(validator.validateCpf("12345678900")).isEqualTo(invalidFirst);
    }

    @ParameterizedTest(name = "CPF de teste \"{0}\" continua invalido")
    @ValueSource(strings = {"00000000000", "00011111111"})
    @DisplayName("deve recusar CPF de teste ainda que o legado o aceite em producao")
    void deve_recusar_cpf_de_teste_ainda_que_o_legado_o_aceite(String cpf) {
        // REQ-DOC-009, nivel C. VALBENEF.NSN:229-245 aceita digitos repetidos
        // quando o CPF comeca em 000, tratando-os como documento de teste.
        assertThat(validator.validateCpf(cpf).invalid()).isTrue();
    }

    @Test
    @DisplayName("deve aplicar a mesma regra de CPF em todos os pontos de entrada")
    void deve_aplicar_a_mesma_regra_de_cpf_em_todos_os_pontos_de_entrada() {
        // ADR-0005: o legado mantem cinco implementacoes com quatro comportamentos.
        // A fachada e o value object precisam concordar sempre.
        String cpf = "11111111111";

        assertThat(validator.validateCpf(cpf).valid()).isFalse();
        assertThat(Cpf.tryParse(cpf)).isEmpty();
    }
}
