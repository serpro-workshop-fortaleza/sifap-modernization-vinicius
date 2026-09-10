package br.gov.sifap.beneficiary.internal;

import br.gov.sifap.beneficiary.AddDependentCommand;
import br.gov.sifap.beneficiary.BeneficiaryQuery;
import br.gov.sifap.beneficiary.BeneficiaryView;
import br.gov.sifap.beneficiary.ChangeBeneficiaryStatusCommand;
import br.gov.sifap.beneficiary.RegisterBeneficiaryCommand;
import br.gov.sifap.beneficiary.UpdateBeneficiaryCommand;
import br.gov.sifap.shared.document.Cpf;
import br.gov.sifap.shared.document.Nis;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.ActorType;
import br.gov.sifap.shared.exception.DomainRuleException;
import br.gov.sifap.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
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

/**
 * Substitui as telas 3270 do {@code CADBENEF}, {@code CADDEPEN} e {@code CONSBENF}.
 *
 * <p>O autor chega por cabecalho enquanto nao existe autenticacao. A origem do
 * {@link Actor} e fatia futura; o contrato que o exige, nao.
 */
@RestController
@RequestMapping("/api/v1/beneficiaries")
class BeneficiaryController {

    private final BeneficiaryService service;
    private final BeneficiaryQuery query;

    BeneficiaryController(BeneficiaryService service, BeneficiaryQuery query) {
        this.service = service;
        this.query = query;
    }

    @PostMapping
    ResponseEntity<BeneficiaryView> register(
            @Valid @RequestBody RegisterBeneficiaryCommand command,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        BeneficiaryView view = service.register(command, actor(actorId, actorProfile));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @PutMapping("/{cpf}")
    BeneficiaryView update(
            @PathVariable String cpf,
            @Valid @RequestBody UpdateBeneficiaryCommand command,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        return service.update(validCpf(cpf).value(), command, actor(actorId, actorProfile));
    }

    @PostMapping("/{cpf}/status")
    BeneficiaryView changeStatus(
            @PathVariable String cpf,
            @Valid @RequestBody ChangeBeneficiaryStatusCommand command,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        return service.changeStatus(validCpf(cpf).value(), command, actor(actorId, actorProfile));
    }

    @PostMapping("/{cpf}/dependents")
    ResponseEntity<BeneficiaryView> addDependent(
            @PathVariable String cpf,
            @Valid @RequestBody AddDependentCommand command,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        BeneficiaryView view =
                service.addDependent(validCpf(cpf).value(), command, actor(actorId, actorProfile));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/{cpf}")
    BeneficiaryView findByCpf(
            @PathVariable String cpf,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        return query.findByCpf(validCpf(cpf), actor(actorId, actorProfile))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REQ-BEN-015", "beneficiario nao encontrado"));
    }

    @GetMapping(params = "nis")
    BeneficiaryView findByNis(
            @RequestParam String nis,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        Nis parsed = Nis.tryParse(nis)
                .orElseThrow(() -> new DomainRuleException("REQ-BEN-015", "NIS invalido"));
        return query.findByNis(parsed, actor(actorId, actorProfile))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REQ-BEN-015", "beneficiario nao encontrado"));
    }

    private static Cpf validCpf(String cpf) {
        return Cpf.tryParse(cpf)
                .orElseThrow(() -> new DomainRuleException("REQ-BEN-003", "CPF invalido"));
    }

    private static Actor actor(String id, String profile) {
        if (id == null || id.isBlank() || profile == null || profile.isBlank()) {
            throw new DomainRuleException("REQ-BEN-019", "autor e perfil sao obrigatorios");
        }
        return new Actor(id, profile, ActorType.HUMANO);
    }
}
