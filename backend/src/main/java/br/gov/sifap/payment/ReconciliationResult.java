package br.gov.sifap.payment;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de um ciclo de conciliacao.
 *
 * <p>Atende {@code REQ-REC-014}. O {@code BATCHCON.NSP:262-272} imprime os totais no
 * {@code CMPRINT} de uma execucao manual, e o resumo nao sobrevive a ela.
 *
 * @param withinTolerance conciliacoes aceitas por diferenca de ate um centavo
 *     ({@code REQ-REC-006}); contado porque ninguem sabe o volume
 */
public record ReconciliationResult(
        String runId,
        String fileSha256,
        String referencePeriod,
        int recordsRead,
        int skipped,
        int reconciled,
        int withinTolerance,
        int divergent,
        int pending,
        BigDecimal confirmedTotal,
        List<ReconciliationIssueView> issues) {

    public ReconciliationResult {
        issues = List.copyOf(issues);
    }

    public boolean hasPendingItems() {
        return pending > 0 || divergent > 0;
    }

    /** @param maskedCpf documento mascarado; a pendencia nao justifica expor dado pessoal */
    public record ReconciliationIssueView(String type, String maskedCpf, String detail) {
    }
}
