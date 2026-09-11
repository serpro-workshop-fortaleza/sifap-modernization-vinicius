package br.gov.sifap.payment.internal.migration;

import br.gov.sifap.payment.ReconciliationStatus;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga da conciliacao historica e reducao do conjunto ambiguo de duplicados.
 *
 * <p>Atende {@code REQ-REC-009} e apoia a decisao que a Fatia 4 deixou em aberto.
 *
 * <p><strong>Nao recalcula e nao escolhe.</strong> A Fatia 4 identificou os pagamentos
 * duplicados do historico e nao decidiu qual vale, porque faltava evidencia. A conciliacao
 * fornece essa evidencia: o que o banco pagou. Um duplicado cujo credito bancario case com
 * apenas um dos registros tem vencedor determinavel; um que nao case com nenhum permanece
 * ambiguo, e a decisao sobre ele nao e tecnica.
 *
 * <p>Usa consulta direta porque a operacao e de apuracao sobre milhoes de linhas: carregar
 * os agregados para contar seria o problema de heap que a Fatia 5 ja evitou nos relatorios.
 */
@Component
public class ReconciliationHistoryLoader {

    private final JdbcTemplate jdbcTemplate;

    public ReconciliationHistoryLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public ReconciliationMigrationReport inventory(String referencePeriod) {
        int read = count(referencePeriod, null);
        int reconciled = count(referencePeriod, ReconciliationStatus.CONCILIADO);
        int divergent = count(referencePeriod, ReconciliationStatus.DIVERGENTE);
        int neverReconciled = countNeverReconciled(referencePeriod);

        Duplicates duplicates = classifyDuplicates(referencePeriod);

        Map<String, Long> statusOutOfDomain = new LinkedHashMap<>();
        long orphanCredit = countOrphanCredit(referencePeriod);
        if (orphanCredit > 0) {
            // Credito registrado sem situacao de conciliacao: o BATCHCON grava DT-CREDIT
            // em BATCHCON.NSP:209 e nunca toca STAT-RECONCIL.
            statusOutOfDomain.put("credito_sem_situacao_de_conciliacao", orphanCredit);
        }

        return new ReconciliationMigrationReport(
                read,
                neverReconciled,
                reconciled,
                divergent,
                duplicates.resolved(),
                duplicates.ambiguous(),
                statusOutOfDomain);
    }

    private int count(String referencePeriod, ReconciliationStatus status) {
        String sql = status == null
                ? "SELECT count(*) FROM payment WHERE reference_period = ?"
                : "SELECT count(*) FROM payment WHERE reference_period = ? AND reconciliation_status = '"
                        + status.name() + "'";
        Integer total = jdbcTemplate.queryForObject(sql, Integer.class, referencePeriod);
        return total == null ? 0 : total;
    }

    private int countNeverReconciled(String referencePeriod) {
        Integer total = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM payment
                 WHERE reference_period = ?
                   AND reconciliation_status IS NULL
                   AND status IN ('EMITIDO', 'CONFIRMADO')
                """,
                Integer.class,
                referencePeriod);
        return total == null ? 0 : total;
    }

    private long countOrphanCredit(String referencePeriod) {
        Long total = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM payment
                 WHERE reference_period = ?
                   AND credit_date IS NOT NULL
                   AND reconciliation_status IS NULL
                """,
                Long.class,
                referencePeriod);
        return total == null ? 0 : total;
    }

    /**
     * Separa os duplicados resolviveis dos que permanecem ambiguos.
     *
     * <p>A restricao {@code uq_payment_cpf_period} impede duplicados novos; os que existem
     * vem da carga da Fatia 4, que os contou em {@code duplicatedInPeriod} sem escolher.
     */
    private Duplicates classifyDuplicates(String referencePeriod) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT cpf,
                       count(*)                                        AS total,
                       count(*) FILTER (WHERE amount_reconciled IS NOT NULL) AS credited
                  FROM payment
                 WHERE reference_period = ?
                 GROUP BY cpf
                HAVING count(*) > 1
                """,
                referencePeriod);

        int resolved = 0;
        int ambiguous = 0;
        for (Map<String, Object> row : rows) {
            long credited = ((Number) row.get("credited")).longValue();
            if (credited == 1) {
                resolved++;
            } else {
                ambiguous++;
            }
        }
        return new Duplicates(resolved, ambiguous);
    }

    /** Soma dos creditos confirmados; existe para conferir a carga contra o extrato. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> confirmedTotal(String referencePeriod) {
        return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                SELECT sum(amount_reconciled) FROM payment
                 WHERE reference_period = ? AND reconciliation_status = 'CONCILIADO'
                """,
                BigDecimal.class,
                referencePeriod));
    }

    private record Duplicates(int resolved, int ambiguous) {
    }
}
