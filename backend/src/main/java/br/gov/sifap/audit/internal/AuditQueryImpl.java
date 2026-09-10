package br.gov.sifap.audit.internal;

import br.gov.sifap.audit.AuditEventView;
import br.gov.sifap.audit.AuditQuery;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class AuditQueryImpl implements AuditQuery {

    private final AuditChangeEventRepository changeRepository;
    private final AuditAccessEventRepository accessRepository;

    AuditQueryImpl(
            AuditChangeEventRepository changeRepository,
            AuditAccessEventRepository accessRepository) {
        this.changeRepository = changeRepository;
        this.accessRepository = accessRepository;
    }

    @Override
    public List<AuditEventView> findChangesByEntity(
            String entityType, String entityId, Instant from, Instant to) {
        return changeRepository.findByEntityAndPeriod(entityType, entityId, from, to).stream()
                .map(AuditQueryImpl::toView)
                .toList();
    }

    @Override
    public List<AuditEventView> findChangesBySubject(String subjectCpf, Instant from, Instant to) {
        return changeRepository.findBySubjectAndPeriod(subjectCpf, from, to).stream()
                .map(AuditQueryImpl::toView)
                .toList();
    }

    @Override
    public List<AuditEventView> findAccessesBySubject(String subjectCpf, Instant from, Instant to) {
        return accessRepository.findBySubjectAndPeriod(subjectCpf, from, to).stream()
                .map(AuditQueryImpl::toView)
                .toList();
    }

    @Override
    public List<AuditEventView> findChangesByBatchRun(String batchRunId) {
        return changeRepository.findByBatchRunIdOrderByIdAsc(batchRunId).stream()
                .map(AuditQueryImpl::toView)
                .toList();
    }

    private static AuditEventView toView(AuditChangeEvent event) {
        return new AuditEventView(
                event.id(),
                event.occurredAt(),
                event.action(),
                event.entityType(),
                event.entityId(),
                event.subjectCpf(),
                event.actor(),
                event.changes(),
                event.batchRunId());
    }

    private static AuditEventView toView(AuditAccessEvent event) {
        return new AuditEventView(
                event.id(),
                event.occurredAt(),
                event.action(),
                event.entityType(),
                event.entityId(),
                event.subjectCpf(),
                event.actor(),
                Map.of(),
                Optional.empty());
    }
}
