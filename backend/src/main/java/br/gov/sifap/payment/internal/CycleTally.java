package br.gov.sifap.payment.internal;

import br.gov.sifap.payment.BenefitCalculation;
import br.gov.sifap.payment.PayrollCycleResult.PayrollRejection;
import br.gov.sifap.shared.document.Cpf;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Acumulador de um ciclo.
 *
 * <p>Mutavel de proposito e de vida curta: existe apenas durante a execucao, e substitui os
 * contadores globais que {@code BATCHPGT.NSP:104-110} declara em {@code DEFINE DATA LOCAL}
 * e que sobrevivem entre iteracoes sem ninguem zerar.
 */
final class CycleTally {

    private static final int MAX_REPORTED_REJECTIONS = 100;

    private int generated;
    private int alreadyPaid;
    private int rejected;
    private BigDecimal totalGross = BigDecimal.ZERO;
    private BigDecimal totalNet = BigDecimal.ZERO;
    private final List<PayrollRejection> rejections = new ArrayList<>();

    void accumulate(BenefitCalculation calculation) {
        generated++;
        totalGross = totalGross.add(calculation.grossAmount());
        totalNet = totalNet.add(calculation.netAmount());
    }

    void skipAlreadyPaid() {
        alreadyPaid++;
    }

    void reject(String cpf, List<String> reasons) {
        rejected++;
        if (rejections.size() < MAX_REPORTED_REJECTIONS) {
            rejections.add(new PayrollRejection(Cpf.mask(cpf), reasons));
        }
    }

    int generated() {
        return generated;
    }

    int alreadyPaid() {
        return alreadyPaid;
    }

    int rejected() {
        return rejected;
    }

    BigDecimal totalGross() {
        return totalGross;
    }

    BigDecimal totalNet() {
        return totalNet;
    }

    List<PayrollRejection> rejections() {
        return List.copyOf(rejections);
    }
}
