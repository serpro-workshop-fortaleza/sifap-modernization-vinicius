package br.gov.sifap.socialprogram.internal;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.ActorType;
import br.gov.sifap.shared.exception.DomainRuleException;
import br.gov.sifap.shared.exception.ResourceNotFoundException;
import br.gov.sifap.socialprogram.ChangeSocialProgramStatusCommand;
import br.gov.sifap.socialprogram.RegisterSocialProgramCommand;
import br.gov.sifap.socialprogram.ReplaceCalculationBandsCommand;
import br.gov.sifap.socialprogram.ReplaceRegionalParametersCommand;
import br.gov.sifap.socialprogram.SocialProgramQuery;
import br.gov.sifap.socialprogram.SocialProgramStatus;
import br.gov.sifap.socialprogram.SocialProgramView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Substitui a tela 3270 do {@code CADPROG} e acrescenta as operacoes que ela nao tem. */
@RestController
@RequestMapping("/api/v1/social-programs")
class SocialProgramController {

    private final SocialProgramService service;
    private final SocialProgramQuery query;

    SocialProgramController(SocialProgramService service, SocialProgramQuery query) {
        this.service = service;
        this.query = query;
    }

    @PostMapping
    ResponseEntity<SocialProgramView> register(
            @Valid @RequestBody RegisterSocialProgramCommand command,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        SocialProgramView view = service.register(command, actor(actorId, actorProfile));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @PutMapping("/{code}")
    SocialProgramView update(
            @PathVariable String code,
            @Valid @RequestBody br.gov.sifap.socialprogram.UpdateSocialProgramCommand command,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        return service.update(code, command, actor(actorId, actorProfile));
    }

    @PostMapping("/{code}/status")
    SocialProgramView changeStatus(
            @PathVariable String code,
            @Valid @RequestBody ChangeSocialProgramStatusCommand command,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        return service.changeStatus(code, command, actor(actorId, actorProfile));
    }

    @PutMapping("/{code}/bands")
    SocialProgramView replaceBands(
            @PathVariable String code,
            @Valid @RequestBody ReplaceCalculationBandsCommand command,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        return service.replaceBands(code, command, actor(actorId, actorProfile));
    }

    @PutMapping("/{code}/regions")
    SocialProgramView replaceRegions(
            @PathVariable String code,
            @Valid @RequestBody ReplaceRegionalParametersCommand command,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        return service.replaceRegions(code, command, actor(actorId, actorProfile));
    }

    @GetMapping("/{code}")
    SocialProgramView findByCode(@PathVariable String code) {
        return query.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REQ-PRG-001", "programa social nao encontrado"));
    }

    @GetMapping
    List<SocialProgramView> findByStatus(
            @RequestParam(defaultValue = "ATIVO") SocialProgramStatus status) {
        return query.findByStatus(status);
    }

    private static Actor actor(String id, String profile) {
        if (id == null || id.isBlank() || profile == null || profile.isBlank()) {
            throw new DomainRuleException("REQ-PRG-013", "autor e perfil sao obrigatorios");
        }
        return new Actor(id, profile, ActorType.HUMANO);
    }
}
