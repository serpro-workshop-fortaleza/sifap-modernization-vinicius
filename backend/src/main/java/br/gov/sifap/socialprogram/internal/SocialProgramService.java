package br.gov.sifap.socialprogram.internal;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.Change;
import br.gov.sifap.shared.exception.ResourceConflictException;
import br.gov.sifap.shared.exception.ResourceNotFoundException;
import br.gov.sifap.socialprogram.ChangeSocialProgramStatusCommand;
import br.gov.sifap.socialprogram.RegisterSocialProgramCommand;
import br.gov.sifap.socialprogram.ReplaceCalculationBandsCommand;
import br.gov.sifap.socialprogram.ReplaceRegionalParametersCommand;
import br.gov.sifap.socialprogram.SocialProgramStatus;
import br.gov.sifap.socialprogram.SocialProgramView;
import br.gov.sifap.socialprogram.UpdateSocialProgramCommand;
import br.gov.sifap.socialprogram.event.SocialProgramRegistered;
import br.gov.sifap.socialprogram.event.SocialProgramStatusChanged;
import br.gov.sifap.socialprogram.event.SocialProgramUpdated;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class SocialProgramService {

    private final SocialProgramRepository repository;
    private final AdjustmentCoefficientRepository coefficients;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SocialProgramService(
            SocialProgramRepository repository,
            AdjustmentCoefficientRepository coefficients,
            ApplicationEventPublisher events,
            Clock clock) {
        this.repository = repository;
        this.coefficients = coefficients;
        this.events = events;
        this.clock = clock;
    }

    SocialProgramView register(RegisterSocialProgramCommand command, Actor actor) {
        SocialProgram program = SocialProgram.register(command, actor, clock);

        if (repository.existsByCode(program.code())) {
            throw new ResourceConflictException("REQ-PRG-001", "programa social ja cadastrado");
        }

        repository.save(program);
        events.publishEvent(new SocialProgramRegistered(
                program.code(),
                program.type().name(),
                program.amountBase(),
                actor,
                clock.instant()));
        return SocialProgramMapper.toView(program);
    }

    SocialProgramView update(String code, UpdateSocialProgramCommand command, Actor actor) {
        SocialProgram program = require(code);
        Map<String, Change> changes = program.update(command, actor, clock);

        if (!changes.isEmpty()) {
            events.publishEvent(
                    new SocialProgramUpdated(program.code(), changes, actor, clock.instant()));
        }
        return SocialProgramMapper.toView(program);
    }

    SocialProgramView changeStatus(
            String code, ChangeSocialProgramStatusCommand command, Actor actor) {
        SocialProgram program = require(code);
        SocialProgramStatus previous = program.changeStatus(
                command.newStatus(), command.reason(), command.closedAt(), actor, clock);

        events.publishEvent(new SocialProgramStatusChanged(
                program.code(), previous, program.status(), command.reason(), actor, clock.instant()));
        return SocialProgramMapper.toView(program);
    }

    SocialProgramView replaceBands(String code, ReplaceCalculationBandsCommand command, Actor actor) {
        SocialProgram program = require(code);
        program.replaceBands(command);
        publishParameterChange(program, "calculationBands", command.bands().size(), actor);
        return SocialProgramMapper.toView(program);
    }

    SocialProgramView replaceRegions(
            String code, ReplaceRegionalParametersCommand command, Actor actor) {
        SocialProgram program = require(code);
        program.replaceRegions(command);
        publishParameterChange(program, "regionalParameters", command.regions().size(), actor);
        return SocialProgramMapper.toView(program);
    }

    /** Fator derivado equivalente a {@code CADPROG.NSP:124}, calculado sob demanda. */
    BigDecimal derivedFactorOf(String code) {
        SocialProgram program = require(code);
        BigDecimal coefficient = coefficients
                .findEffectiveOn(LocalDate.now(clock))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REQ-PRG-005", "nenhum coeficiente de ajuste vigente"))
                .coefficient();
        return program.derivedFactor(coefficient);
    }

    private void publishParameterChange(
            SocialProgram program, String field, int size, Actor actor) {
        events.publishEvent(new SocialProgramUpdated(
                program.code(),
                Map.of(field, Change.created(String.valueOf(size))),
                actor,
                clock.instant()));
    }

    private SocialProgram require(String code) {
        return repository
                .findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REQ-PRG-001", "programa social nao encontrado"));
    }
}
