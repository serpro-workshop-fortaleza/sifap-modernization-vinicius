package br.gov.sifap.payment.internal;

import br.gov.sifap.payment.ReconciliationStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByCpfAndReferencePeriod(String cpf, String referencePeriod);

    /**
     * CPFs ja pagos no periodo.
     *
     * <p>Atende {@code REQ-PAY-003}. Uma consulta por bloco substitui a verificacao que o
     * {@code BATCHPGT} nao faz: o programa le o cadastro e grava sem perguntar se o periodo
     * ja foi processado, e reexecutar o job duplica a folha inteira.
     */
    @Query("select p.cpf from Payment p where p.referencePeriod = :period and p.cpf in :cpfs")
    List<String> findPaidCpfs(
            @Param("period") String referencePeriod, @Param("cpfs") List<String> cpfs);

    long countByCycleId(String cycleId);

    @Query("select coalesce(sum(p.amountGross), 0) from Payment p where p.cycleId = :cycleId")
    BigDecimal sumGrossByCycle(@Param("cycleId") String cycleId);

    @Query("select coalesce(sum(p.amountNet), 0) from Payment p where p.cycleId = :cycleId")
    BigDecimal sumNetByCycle(@Param("cycleId") String cycleId);

    List<Payment> findByReferencePeriodOrderByCpf(String referencePeriod);

    List<Payment> findByCpfOrderByReferencePeriodDesc(String cpf);

    /** Apoia {@code AC-009.2}; usa o indice parcial de {@code idx_payment_reconciliation}. */
    List<Payment> findByReferencePeriodAndReconciliationStatusOrderByCpf(
            String referencePeriod, ReconciliationStatus reconciliationStatus);
}
