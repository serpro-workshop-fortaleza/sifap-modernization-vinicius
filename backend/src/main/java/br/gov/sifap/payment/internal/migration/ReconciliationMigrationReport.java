package br.gov.sifap.payment.internal.migration;

import java.util.Map;

/**
 * Inventario da conciliacao no historico migrado.
 *
 * <p>Atende {@code REQ-REC-009}. Os campos {@code STAT-RECONCIL} e {@code AMT-RECONCILED}
 * estao declarados em {@code PAYMENT.ddm:95-97} e nenhum programa os grava: <strong>todo o
 * historico chega sem situacao de conciliacao</strong>.
 *
 * <p>O que a carga mede, portanto, nao e quantos divergem — e quantos jamais passaram por
 * conferencia registrada.
 *
 * @param neverReconciled pagamentos emitidos sem nenhuma marca de conferencia
 * @param divergentResolved duplicados cujo credito bancario identifica um vencedor
 * @param stillAmbiguous duplicados sem credito correspondente; decisao de negocio
 */
public record ReconciliationMigrationReport(
        int paymentsRead,
        int neverReconciled,
        int reconciled,
        int divergent,
        int divergentResolved,
        int stillAmbiguous,
        Map<String, Long> statusOutOfDomain) {

    public ReconciliationMigrationReport {
        statusOutOfDomain = Map.copyOf(statusOutOfDomain);
    }

    public boolean hasUnreconciledHistory() {
        return neverReconciled > 0;
    }
}
