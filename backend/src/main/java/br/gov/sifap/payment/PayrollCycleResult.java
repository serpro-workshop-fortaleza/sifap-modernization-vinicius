package br.gov.sifap.payment;

import java.util.List;

/**
 * Resultado de um ciclo de folha.
 *
 * <p>Atende {@code REQ-PAY-023}. {@code BATCHPGT.NSP:536-546} imprime apenas totais; os
 * CPFs ignorados nao aparecem em lugar nenhum, e quem reclama de nao ter recebido nao tem
 * como saber por que.
 *
 * @param rejections motivo por CPF, limitado ao que couber em uma resposta; a lista
 *     completa vive na trilha de auditoria
 */
public record PayrollCycleResult(
        String cycleId,
        String referencePeriod,
        int generated,
        int alreadyPaid,
        int rejected,
        java.math.BigDecimal totalGross,
        java.math.BigDecimal totalNet,
        List<PayrollRejection> rejections) {

    public PayrollCycleResult {
        rejections = List.copyOf(rejections);
    }

    public record PayrollRejection(String maskedCpf, List<String> reasons) {
        public PayrollRejection {
            reasons = List.copyOf(reasons);
        }
    }
}
