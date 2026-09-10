package br.gov.sifap.socialprogram.event;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Programa social incluido.
 *
 * <p>Origem no legado: {@code CADPROG.NSP:139-147}.
 *
 * <p>Nao carrega CPF afetado: {@code CADPROG.NSP:144} faz {@code RESET #AUD-CPF}, porque o
 * evento nao e sobre pessoa. E o primeiro contexto cujos eventos nao tem sujeito pessoal, e
 * a trilha ja o comporta — {@code subjectCpf()} e {@code Optional} desde a Fatia 1.
 */
public record SocialProgramRegistered(
        String programCode, String type, BigDecimal amountBase, Actor actor, Instant occurredAt)
        implements AuditableEvent {

    @Override
    public AuditAction action() {
        return AuditAction.INCLUSAO;
    }

    @Override
    public String entityType() {
        return "SOCIAL_PROGRAM";
    }

    @Override
    public String entityId() {
        return programCode;
    }
}
