package br.gov.sifap.audit.internal;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditChangeEventRepository extends JpaRepository<AuditChangeEvent, Long> {

    @Query("""
            SELECT e FROM AuditChangeEvent e
            WHERE e.entityType = :entityType
              AND e.entityId = :entityId
              AND e.occurredAt >= :from AND e.occurredAt < :to
            ORDER BY e.occurredAt DESC, e.id DESC
            """)
    List<AuditChangeEvent> findByEntityAndPeriod(
            @Param("entityType") String entityType,
            @Param("entityId") String entityId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT e FROM AuditChangeEvent e
            WHERE e.subjectCpf = :subjectCpf
              AND e.occurredAt >= :from AND e.occurredAt < :to
            ORDER BY e.occurredAt DESC, e.id DESC
            """)
    List<AuditChangeEvent> findBySubjectAndPeriod(
            @Param("subjectCpf") String subjectCpf,
            @Param("from") Instant from,
            @Param("to") Instant to);

    List<AuditChangeEvent> findByBatchRunIdOrderByIdAsc(String batchRunId);
}
