package br.gov.sifap.payment.internal;

import br.gov.sifap.shared.event.AuditableEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica eventos que nascem fora de uma transacao de negocio.
 *
 * <p>O ciclo de folha nao e transacional — nenhuma transacao sobrevive a janela de quatro
 * horas — mas o registro do seu encerramento precisa de uma. Sem isto, o evento e
 * publicado, o listener {@code BEFORE_COMMIT} nunca dispara e a conclusao da folha some:
 * exatamente o silencio que o {@code REQ-AUD-010} existe para eliminar.
 */
@Service
public class PayrollEventRecorder {

    private final ApplicationEventPublisher publisher;

    PayrollEventRecorder(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditableEvent event) {
        publisher.publishEvent(event);
    }
}
