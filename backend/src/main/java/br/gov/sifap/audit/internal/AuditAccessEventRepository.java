package br.gov.sifap.audit.internal;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditAccessEventRepository extends JpaRepository<AuditAccessEvent, Long> {

    @Query("""
            SELECT e FROM AuditAccessEvent e
            WHERE e.subjectCpf = :subjectCpf
              AND e.occurredAt >= :from AND e.occurredAt < :to
            ORDER BY e.occurredAt DESC, e.id DESC
            """)
    List<AuditAccessEvent> findBySubjectAndPeriod(
            @Param("subjectCpf") String subjectCpf,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
