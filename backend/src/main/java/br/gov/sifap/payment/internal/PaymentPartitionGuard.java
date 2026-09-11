package br.gov.sifap.payment.internal;

import br.gov.sifap.shared.exception.DomainRuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica se a particao do periodo existe antes de iniciar o ciclo.
 *
 * <p>A aplicacao nao cria particao — nao tem direito de DDL. O que ela pode fazer e
 * recusar a folha com um motivo legivel em vez de falhar no meio da gravacao, no bloco
 * 400, com {@code no partition of relation found for row}.
 */
@Component
class PaymentPartitionGuard {

    private final JdbcTemplate jdbcTemplate;

    PaymentPartitionGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    void requirePartition(String referencePeriod) {
        String partition = "payment_" + referencePeriod;
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL", Boolean.class, partition);

        if (!Boolean.TRUE.equals(exists)) {
            throw new DomainRuleException(
                    "REQ-PAY-022",
                    "particao de folha ausente para o periodo " + referencePeriod
                            + "; a manutencao de particoes precisa ser executada antes do ciclo");
        }
    }
}
