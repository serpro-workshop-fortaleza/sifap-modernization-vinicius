package br.gov.sifap.payment.internal.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.payment.PaymentStatus;
import br.gov.sifap.payment.ReconciliationStatus;
import br.gov.sifap.payment.ReturnCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Divergencias deliberadas em relacao ao legado.
 *
 * <p>Cada teste aqui <strong>falharia</strong> contra o SIFAP original. Nenhuma das nove
 * correcoes desta fatia altera valor de beneficio: a conciliacao nao calcula, compara.
 */
@DisplayName("Divergencias do legado na conciliacao")
class LegacyDivergenceReconciliationTest {

    /**
     * {@code SIFAP-F5-01}. {@code BATCHCON.NSP:203} faz {@code ADD 1 TO #QTY-RECONCILED}
     * antes do {@code DECIDE}, e o ramo {@code NONE} de {@code :237-242} apenas escreve no
     * log. O resumo declara sucesso sobre registros que ninguem tratou.
     */
    @Test
    @DisplayName("deve contar o conciliado so depois da transicao onde o legado contava antes")
    void deve_contar_o_conciliado_so_depois_da_transicao_onde_o_legado_contava_antes() {
        ReconciliationTally tally = new ReconciliationTally();

        tally.recordRead();
        tally.recordPending();

        assertThat(tally.reconciled()).isZero();
        assertThat(tally.pending()).isEqualTo(1);
    }

    /**
     * {@code REQ-REC-008}. O legado trata o codigo desconhecido com um ramo padrao que
     * segue adiante; aqui ele nao produz valor nenhum.
     */
    @Test
    @DisplayName("deve devolver vazio onde o legado seguia adiante com codigo desconhecido")
    void deve_devolver_vazio_onde_o_legado_seguia_adiante_com_codigo_desconhecido() {
        assertThat(ReturnCode.fromCode("00")).contains(ReturnCode.CREDITADO);
        assertThat(ReturnCode.fromCode("77")).isEmpty();
        assertThat(ReturnCode.fromCode("  ")).isEmpty();
        assertThat(ReturnCode.fromCode(null)).isEmpty();
    }

    /**
     * {@code REQ-REC-011}. {@code BATCHCON.NSP:205-213} grava a nova situacao sem consultar
     * a anterior: um retorno atrasado sobrescreve um cancelamento.
     */
    @Test
    @DisplayName("deve admitir retorno apenas do pagamento remetido onde o legado nao consultava")
    void deve_admitir_retorno_apenas_do_pagamento_remetido_onde_o_legado_nao_consultava() {
        assertThat(PaymentStatus.EMITIDO.acceptsBankReturn()).isTrue();
        assertThat(PaymentStatus.GERADO.acceptsBankReturn()).isFalse();
        assertThat(PaymentStatus.CANCELADO.acceptsBankReturn()).isFalse();
        assertThat(PaymentStatus.CONFIRMADO.acceptsBankReturn()).isFalse();
    }

    /**
     * {@code REQ-REC-016}. {@code BATCHCON.NSP:212} faz {@code MOVE 1 TO COD-BANK} sobre um
     * campo {@code A3} declarado como codigo FEBRABAN em {@code PAYMENT.ddm:72}.
     */
    @Test
    @DisplayName("deve exigir codigo de banco do dominio onde o legado gravava um literal")
    void deve_exigir_codigo_de_banco_do_dominio_onde_o_legado_gravava_um_literal() {
        assertThat(BankCode.of("001").value()).isEqualTo("001");

        assertThatThrownBy(() -> BankCode.of("1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora do dominio");
    }

    /**
     * {@code REQ-REC-009}. O dominio de {@code STAT-RECONCIL} esta declarado em
     * {@code PAYMENT.ddm:95} desde sempre e nenhum programa grava o campo.
     */
    @Test
    @DisplayName("deve converter o dominio declarado no dicionario que o legado nunca gravou")
    void deve_converter_o_dominio_declarado_no_dicionario_que_o_legado_nunca_gravou() {
        assertThat(ReconciliationStatus.fromLegacyCode("C")).contains(ReconciliationStatus.CONCILIADO);
        assertThat(ReconciliationStatus.fromLegacyCode("D")).contains(ReconciliationStatus.DIVERGENTE);
        assertThat(ReconciliationStatus.fromLegacyCode(" ")).isEmpty();
    }

    /**
     * {@code REQ-REC-003}. O resumo do conteudo distingue arquivos que o nome nao distingue:
     * {@code BATCHCON.NSP:139-141} registra que o nome digitado e documentacao.
     */
    @Test
    @DisplayName("deve identificar o arquivo pelo conteudo onde o legado nao identificava")
    void deve_identificar_o_arquivo_pelo_conteudo_onde_o_legado_nao_identificava() {
        String first = FileDigest.of(java.util.List.of("linha 1", "linha 2"));
        String same = FileDigest.of(java.util.List.of("linha 1", "linha 2"));
        String other = FileDigest.of(java.util.List.of("linha 1", "linha 3"));

        assertThat(first).isEqualTo(same).hasSize(64).isNotEqualTo(other);
    }

    /**
     * {@code REQ-REC-006}. A tolerancia permanece em {@code PS}; o que muda e que cada uso
     * passa a ser contavel.
     */
    @Test
    @DisplayName("deve contar a tolerancia separadamente onde o legado a deixava invisivel")
    void deve_contar_a_tolerancia_separadamente_onde_o_legado_a_deixava_invisivel() {
        ReconciliationTally tally = new ReconciliationTally();

        tally.recordReconciled(new BigDecimal("100.00"));
        tally.recordWithinTolerance();

        assertThat(tally.reconciled()).isEqualTo(1);
        assertThat(tally.withinTolerance()).isEqualTo(1);
        assertThat(tally.confirmedTotal()).isEqualByComparingTo("100.00");
    }
}
