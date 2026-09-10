package br.gov.sifap.beneficiary.event;

import br.gov.sifap.beneficiary.BeneficiaryStatus;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.Change;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Situacao cadastral alterada.
 *
 * <p>Evento proprio por exigencia do {@code SIFAP-M-01}, em nivel {@code PS}: a suspensao
 * automatica acima de 75 anos e preservada, mas cada ocorrencia precisa ser identificavel.
 * No legado ela e indistinguivel de uma troca de telefone.
 *
 * @param previousStatus nulo quando o registro migrado nao tinha situacao confiavel
 * @param ageAtChange idade apurada, para tornar mensuravel o efeito do {@code REQ-BEN-007};
 *     nulo quando a origem migrada nao trouxe data de nascimento legivel
 */
public record BeneficiaryStatusChanged(
        String cpf,
        BeneficiaryStatus previousStatus,
        BeneficiaryStatus newStatus,
        String reason,
        Integer ageAtChange,
        Actor actor,
        Instant occurredAt)
        implements AuditableEvent {

    @Override
    public AuditAction action() {
        return AuditAction.ALTERACAO;
    }

    @Override
    public String entityType() {
        return "BENEFICIARY";
    }

    @Override
    public String entityId() {
        return cpf;
    }

    @Override
    public Optional<String> subjectCpf() {
        return Optional.of(cpf);
    }

    @Override
    public Map<String, Change> changes() {
        return Map.of(
                "status",
                new Change(
                        previousStatus == null ? null : previousStatus.name(), newStatus.name()),
                "statusReason",
                Change.created(reason),
                "ageAtChange",
                Change.created(ageAtChange == null ? "desconhecida" : String.valueOf(ageAtChange)));
    }
}
