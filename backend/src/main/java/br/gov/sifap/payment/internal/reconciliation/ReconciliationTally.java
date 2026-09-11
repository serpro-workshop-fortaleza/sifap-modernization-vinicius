package br.gov.sifap.payment.internal.reconciliation;

import java.math.BigDecimal;

/**
 * Contadores de um ciclo de conciliacao.
 *
 * <p>Atende {@code REQ-REC-008} e {@code REQ-REC-014}.
 *
 * <p>{@link #recordReconciled(BigDecimal)} so pode ser chamado depois que a transicao foi aplicada. O
 * defeito do {@code SIFAP-F5-01} e de ordem de instrucoes: {@code BATCHCON.NSP:203} faz
 * {@code ADD 1 TO #QTY-RECONCILED} <strong>antes</strong> do {@code DECIDE} que classifica
 * o codigo, e o ramo {@code NONE} apenas escreve no log.
 */
final class ReconciliationTally {

    private int read;
    private int skipped;
    private int reconciled;
    private int withinTolerance;
    private int divergent;
    private int pending;
    private BigDecimal confirmedTotal = BigDecimal.ZERO;

    void recordRead() {
        read++;
    }

    void recordSkipped() {
        skipped++;
    }

    void recordReconciled(BigDecimal bankAmount) {
        reconciled++;
        confirmedTotal = confirmedTotal.add(bankAmount);
    }

    /** {@code REQ-REC-006}: a tolerancia permanece e cada uso fica contavel. */
    void recordWithinTolerance() {
        withinTolerance++;
    }

    void recordDivergent() {
        divergent++;
    }

    void recordPending() {
        pending++;
    }

    boolean hasPendingItems() {
        return pending > 0 || divergent > 0;
    }

    int read() {
        return read;
    }

    int skipped() {
        return skipped;
    }

    int reconciled() {
        return reconciled;
    }

    int withinTolerance() {
        return withinTolerance;
    }

    int divergent() {
        return divergent;
    }

    int pending() {
        return pending;
    }

    BigDecimal confirmedTotal() {
        return confirmedTotal;
    }
}
