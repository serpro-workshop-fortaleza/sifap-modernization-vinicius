package br.gov.sifap.payment.internal.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Registro de detalhe do arquivo de retorno, ja tipado.
 *
 * <p>Existe para que o resto da conciliacao nunca veja posicoes de campo. O legado espalha
 * {@code SUBSTR} pelo corpo do programa ({@code BATCHCON.NSP:150-164}), e o comentario de
 * {@code :166-168} registra o custo: ate 2017 o valor alfanumerico ia direto para o campo
 * decimal, sem dividir por cem.
 *
 * @param lineNumber posicao no arquivo, para que a pendencia seja localizavel
 * @param paymentNumber vazio quando o arquivo nao traz numero utilizavel
 */
record ReturnRecord(
        int lineNumber,
        String bankCode,
        String cpf,
        Optional<String> paymentNumber,
        BigDecimal amount,
        LocalDate paymentDate,
        String returnCode) {
}
