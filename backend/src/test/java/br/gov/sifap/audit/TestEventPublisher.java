package br.gov.sifap.audit;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica eventos dentro de uma transacao, como fariam os contextos de origem.
 *
 * <p>Necessario porque {@code @TransactionalEventListener} so e acionado quando existe
 * uma transacao ativa — que e justamente a garantia sob teste.
 */
public class TestEventPublisher {

    private final ApplicationEventPublisher publisher;

    TestEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Transactional
    public void publish(Object... events) {
        for (Object event : events) {
            publisher.publishEvent(event);
        }
    }

    /** Simula a transacao de negocio que falha depois de publicar o evento. */
    @Transactional
    public void publishThenFail(Object event) {
        publisher.publishEvent(event);
        throw new IllegalStateException("falha deliberada apos publicar o evento");
    }

    @TestConfiguration
    public static class Config {

        @Bean
        TestEventPublisher testEventPublisher(ApplicationEventPublisher publisher) {
            return new TestEventPublisher(publisher);
        }
    }
}
