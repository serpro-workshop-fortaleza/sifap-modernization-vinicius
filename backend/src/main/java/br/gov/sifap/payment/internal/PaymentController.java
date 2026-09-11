package br.gov.sifap.payment.internal;

import br.gov.sifap.payment.BankRemittance;
import br.gov.sifap.payment.DiscountEntry;
import br.gov.sifap.payment.PaymentQuery;
import br.gov.sifap.payment.PaymentView;
import br.gov.sifap.payment.PayrollCycleResult;
import br.gov.sifap.payment.ReconciliationResult;
import br.gov.sifap.payment.internal.reconciliation.ReconciliationService;
import br.gov.sifap.shared.document.Cpf;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operacoes de folha.
 *
 * <p>O {@code BATCHPGT} so existe como JCL disparado por agendador. Aqui o ciclo tem
 * endpoint, resposta e registro — quem executou a folha de um mes deixa de ser pergunta
 * para o operador de producao.
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentController {

    private static final String PERIOD_PATTERN = "^\\d{6}$";

    private final PayrollCycleService cycleService;
    private final PaymentAdjustmentService adjustmentService;
    private final BankRemittanceService remittanceService;
    private final ReconciliationService reconciliationService;
    private final PaymentQuery query;

    PaymentController(
            PayrollCycleService cycleService,
            PaymentAdjustmentService adjustmentService,
            BankRemittanceService remittanceService,
            ReconciliationService reconciliationService,
            PaymentQuery query) {
        this.cycleService = cycleService;
        this.adjustmentService = adjustmentService;
        this.remittanceService = remittanceService;
        this.reconciliationService = reconciliationService;
        this.query = query;
    }

    @PostMapping("/cycles")
    ResponseEntity<PayrollCycleResult> runCycle(
            @Valid @RequestBody RunCycleRequest request,
            @RequestHeader("X-Sifap-Actor-Id") String actorId) {
        PayrollCycleResult result = cycleService.run(
                request.programCode(), request.referencePeriod(), Actor.process(actorId));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/cycles/{cycleId}/remittance")
    BankRemittance issueRemittance(
            @PathVariable String cycleId,
            @RequestParam @Pattern(regexp = PERIOD_PATTERN) String referencePeriod,
            @RequestHeader("X-Sifap-Actor-Id") String actorId) {
        return remittanceService.issue(cycleId, referencePeriod, Actor.process(actorId));
    }

    @PostMapping("/reconciliations")
    ResponseEntity<ReconciliationResult> reconcile(
            @Valid @RequestBody ReconcileRequest request,
            @RequestHeader("X-Sifap-Actor-Id") String actorId) {
        ReconciliationResult result = reconciliationService.reconcile(
                request.fileName(), request.referencePeriod(), request.lines(), Actor.process(actorId));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{cpf}/periods/{referencePeriod}/discounts")
    ResponseEntity<Void> applyDiscounts(
            @PathVariable String cpf,
            @PathVariable String referencePeriod,
            @Valid @RequestBody List<DiscountEntry> entries,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        adjustmentService.applyDiscounts(
                Cpf.of(cpf).value(), referencePeriod, entries, Actor.human(actorId, actorProfile));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{cpf}/periods/{referencePeriod}/correction")
    ResponseEntity<Void> applyCorrection(
            @PathVariable String cpf,
            @PathVariable String referencePeriod,
            @RequestParam @Pattern(regexp = PERIOD_PATTERN) String indexPeriod,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        adjustmentService.applyCorrection(
                Cpf.of(cpf).value(), referencePeriod, indexPeriod, Actor.human(actorId, actorProfile));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{cpf}")
    List<PaymentView> history(
            @PathVariable String cpf,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        return query.findByCpf(Cpf.of(cpf), Actor.human(actorId, actorProfile));
    }

    @GetMapping("/{cpf}/periods/{referencePeriod}")
    PaymentView byPeriod(
            @PathVariable String cpf,
            @PathVariable String referencePeriod,
            @RequestHeader("X-Sifap-Actor-Id") String actorId,
            @RequestHeader("X-Sifap-Actor-Profile") String actorProfile) {
        return query.findByCpfAndPeriod(
                        Cpf.of(cpf), referencePeriod, Actor.human(actorId, actorProfile))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REQ-PAY-001", "pagamento nao encontrado para o periodo " + referencePeriod));
    }

    record RunCycleRequest(
            @NotBlank String programCode,
            @NotBlank @Pattern(regexp = PERIOD_PATTERN, message = "periodo deve estar no formato AAAAMM")
                    String referencePeriod) {
    }

    /** O conteudo vem no corpo; a identidade do arquivo e o resumo dele, nao o nome. */
    record ReconcileRequest(
            @NotBlank String fileName,
            @NotBlank @Pattern(regexp = PERIOD_PATTERN, message = "periodo deve estar no formato AAAAMM")
                    String referencePeriod,
            @NotEmpty List<String> lines) {
    }
}
