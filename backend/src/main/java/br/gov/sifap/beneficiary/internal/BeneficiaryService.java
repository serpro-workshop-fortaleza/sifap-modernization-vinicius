package br.gov.sifap.beneficiary.internal;

import br.gov.sifap.beneficiary.AddDependentCommand;
import br.gov.sifap.beneficiary.BeneficiaryStatus;
import br.gov.sifap.beneficiary.BeneficiaryView;
import br.gov.sifap.beneficiary.ChangeBeneficiaryStatusCommand;
import br.gov.sifap.beneficiary.RegisterBeneficiaryCommand;
import br.gov.sifap.beneficiary.UpdateBeneficiaryCommand;
import br.gov.sifap.beneficiary.event.BeneficiaryRegistered;
import br.gov.sifap.beneficiary.event.BeneficiaryStatusChanged;
import br.gov.sifap.beneficiary.event.BeneficiaryUpdated;
import br.gov.sifap.beneficiary.event.DependentAdded;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.Change;
import br.gov.sifap.shared.exception.ResourceConflictException;
import br.gov.sifap.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operacoes de escrita do cadastro.
 *
 * <p>Os eventos sao publicados dentro da transacao de negocio, e a trilha os consome em
 * {@code BEFORE_COMMIT}. Preserva o comportamento do {@code BACKOUT} legado, em que dado e
 * auditoria sao desfeitos juntos.
 */
@Service
@Transactional
class BeneficiaryService {

    private final BeneficiaryRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    BeneficiaryService(
            BeneficiaryRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    BeneficiaryView register(RegisterBeneficiaryCommand command, Actor actor) {
        Beneficiary beneficiary = Beneficiary.register(command, actor, clock);

        if (repository.existsByCpf(beneficiary.cpf())) {
            throw new ResourceConflictException("REQ-BEN-001", "beneficiario ja cadastrado");
        }

        repository.save(beneficiary);
        events.publishEvent(new BeneficiaryRegistered(
                beneficiary.cpf(),
                beneficiary.programCode().orElse(null),
                actor,
                clock.instant()));

        applyAgeBasedSuspension(beneficiary, actor);
        return view(beneficiary);
    }

    BeneficiaryView update(String cpf, UpdateBeneficiaryCommand command, Actor actor) {
        Beneficiary beneficiary = require(cpf);
        Map<String, Change> changes = beneficiary.update(command, actor, clock);

        if (!changes.isEmpty()) {
            events.publishEvent(
                    new BeneficiaryUpdated(beneficiary.cpf(), changes, actor, clock.instant()));
        }

        // O legado tambem avalia a idade na alteracao (CADBENEF.NSP:250), e a avaliacao
        // e preservada. O que muda e que a situacao vigente nao e sobrescrita por branco.
        applyAgeBasedSuspension(beneficiary, actor);
        return view(beneficiary);
    }

    BeneficiaryView changeStatus(String cpf, ChangeBeneficiaryStatusCommand command, Actor actor) {
        Beneficiary beneficiary = require(cpf);
        BeneficiaryStatus previous =
                beneficiary.changeStatus(command.newStatus(), command.reason(), actor, clock);

        publishStatusChange(beneficiary, previous, command.reason(), actor);
        return view(beneficiary);
    }

    BeneficiaryView addDependent(String cpf, AddDependentCommand command, Actor actor) {
        Beneficiary beneficiary = require(cpf);
        Dependent dependent = beneficiary.addDependent(command);

        events.publishEvent(new DependentAdded(
                beneficiary.cpf(),
                dependent.fullName(),
                dependent.relation().name(),
                beneficiary.activeDependentCount(),
                actor,
                clock.instant()));

        return view(beneficiary);
    }

    private void applyAgeBasedSuspension(Beneficiary beneficiary, Actor actor) {
        beneficiary
                .applyAgeBasedSuspension(actor, clock)
                .ifPresent(previous -> publishStatusChange(
                        beneficiary,
                        previous,
                        beneficiary.statusReason().orElseThrow(),
                        actor));
    }

    private void publishStatusChange(
            Beneficiary beneficiary, BeneficiaryStatus previous, String reason, Actor actor) {
        events.publishEvent(new BeneficiaryStatusChanged(
                beneficiary.cpf(),
                previous,
                beneficiary.status().orElseThrow(),
                reason,
                beneficiary.ageAt(LocalDate.now(clock)).stream().boxed().findFirst().orElse(null),
                actor,
                clock.instant()));
    }

    private Beneficiary require(String cpf) {
        return repository
                .findByCpf(cpf)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REQ-BEN-001", "beneficiario nao encontrado"));
    }

    private BeneficiaryView view(Beneficiary beneficiary) {
        return BeneficiaryViewMapper.toView(beneficiary, LocalDate.now(clock));
    }
}
