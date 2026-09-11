package br.gov.sifap.payment.internal.migration;

import java.util.Map;

/**
 * Resultado da carga do historico de pagamentos.
 *
 * <p>Atende {@code REQ-PAY-016}. 180 milhoes de registros, e nenhuma fonte do acervo diz
 * quantos deles carregam os defeitos conhecidos. O inventario e o primeiro momento em que
 * isso fica contavel.
 *
 * @param withoutNumber pagamentos sem {@code NUM-PAYMENT}, gravados pelo caminho do
 *     {@code CALCBENF.NSN:319} sobre um campo que {@code PAYMENT.ddm:29} declara unico
 * @param duplicatedInPeriod CPFs com mais de um pagamento no mesmo periodo
 *     ({@code SIFAP-M-06}: reexecutar a folha duplica o mes inteiro)
 * @param inconsistentNet registros em que o liquido nao corresponde a bruto menos desconto
 *     ({@code CALCDSCT.NSP:188} atualiza o desconto e nao recalcula o liquido)
 * @param typeOutOfDomain tipos fora do dominio declarado, incluindo o {@code D} que
 *     {@code CALCBENF.NSN:273} grava em dezembro
 */
public record PaymentMigrationReport(
        int paymentsRead,
        int paymentsMigrated,
        int withoutNumber,
        int duplicatedInPeriod,
        int inconsistentNet,
        Map<String, Long> typeOutOfDomain) {

    public PaymentMigrationReport {
        typeOutOfDomain = Map.copyOf(typeOutOfDomain);
    }

    public boolean noPaymentDiscarded() {
        return paymentsRead == paymentsMigrated;
    }
}
