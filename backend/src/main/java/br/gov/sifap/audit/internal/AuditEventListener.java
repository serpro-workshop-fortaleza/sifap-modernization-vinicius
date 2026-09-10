package br.gov.sifap.audit.internal;

import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.AuditableEventBatch;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Consome os eventos de dominio e grava a trilha.
 *
 * <p>Cobre {@code REQ-AUD-001}, {@code REQ-AUD-007} e {@code REQ-AUD-010}.
 *
 * <p>A fase e {@link TransactionPhase#BEFORE_COMMIT} de proposito. O legado grava a
 * auditoria na mesma transacao do dado ({@code CCAUDIT.NSC:53-59}), de modo que o
 * {@code BACKOUT} desfaz os dois juntos. Um listener assincrono abriria a janela em que
 * o dado existe e a auditoria nao — exatamente o defeito que o {@code REQ-AUD-001}
 * corrige. O teste de rollback existe para impedir essa mudanca silenciosa.
 */
@Component
public class AuditEventListener {

    private final AuditChangeEventRepository changeRepository;
    private final AuditAccessEventRepository accessRepository;

    AuditEventListener(
            AuditChangeEventRepository changeRepository,
            AuditAccessEventRepository accessRepository) {
        this.changeRepository = changeRepository;
        this.accessRepository = accessRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(AuditableEvent event) {
        if (event.action().isAccessToPersonalData()) {
            accessRepository.save(AuditAccessEvent.from(event));
        } else {
            changeRepository.save(AuditChangeEvent.from(event));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(AuditableEventBatch batch) {
        List<AuditChangeEvent> changes = new ArrayList<>();
        List<AuditAccessEvent> accesses = new ArrayList<>();

        for (AuditableEvent event : batch.events()) {
            if (event.action().isAccessToPersonalData()) {
                accesses.add(AuditAccessEvent.from(event));
            } else {
                changes.add(AuditChangeEvent.from(event));
            }
        }

        if (!changes.isEmpty()) {
            changeRepository.saveAll(changes);
        }
        if (!accesses.isEmpty()) {
            accessRepository.saveAll(accesses);
        }
    }
}
