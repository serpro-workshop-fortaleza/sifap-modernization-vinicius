package br.gov.sifap.shared.event;

import java.util.List;
import java.util.Objects;

/**
 * Conjunto de eventos publicado de uma vez por processamento em lote.
 *
 * <p>Existe por desempenho, nao por semantica: a Fatia 4 gera 3,8 milhoes de pagamentos
 * por ciclo dentro de uma janela de 4 horas, e publicar um evento por vez atravessa o
 * listener 3,8 milhoes de vezes. O contrato de cada evento permanece o mesmo.
 */
public record AuditableEventBatch(List<AuditableEvent> events) {

    public AuditableEventBatch {
        Objects.requireNonNull(events, "events");
        events = List.copyOf(events);
    }
}
