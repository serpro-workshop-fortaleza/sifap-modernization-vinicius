package br.gov.sifap.payment.internal;

import br.gov.sifap.beneficiary.PayrollCandidate;
import br.gov.sifap.payment.BenefitCalculation;
import br.gov.sifap.payment.BenefitInput;
import br.gov.sifap.payment.EligibilityResult;
import br.gov.sifap.payment.event.PaymentGenerated;
import br.gov.sifap.payment.internal.calculation.BenefitCalculator;
import br.gov.sifap.payment.internal.calculation.EligibilityChecker;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditableEvent;
import br.gov.sifap.shared.event.AuditableEventBatch;
import br.gov.sifap.socialprogram.SocialProgramView;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processamento de um bloco da folha.
 *
 * <p>Componente proprio, e nao metodo do servico de ciclo, porque a transacao por bloco
 * precisa passar pelo proxy do Spring — chamada interna nao passa.
 *
 * <p>{@code REQUIRES_NEW}: bloco confirmado permanece confirmado. Uma falha no bloco 400
 * nao desfaz os 399 anteriores, e a retomada nao os regrava porque o
 * {@code REQ-PAY-003} filtra por periodo antes de calcular.
 */
@Service
class PayrollBlockProcessor {

    private final PaymentRepository payments;
    private final BenefitCalculator calculator;
    private final EligibilityChecker eligibility;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    PayrollBlockProcessor(
            PaymentRepository payments,
            BenefitCalculator calculator,
            EligibilityChecker eligibility,
            ApplicationEventPublisher publisher,
            Clock clock) {
        this.payments = payments;
        this.calculator = calculator;
        this.eligibility = eligibility;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void process(
            List<PayrollCandidate> block,
            SocialProgramView program,
            String referencePeriod,
            String cycleId,
            Actor actor,
            CycleTally tally) {

        List<String> cpfs = block.stream().map(PayrollCandidate::cpf).toList();
        Set<String> alreadyPaid = new HashSet<>(payments.findPaidCpfs(referencePeriod, cpfs));

        List<Payment> generated = new ArrayList<>(block.size());

        for (PayrollCandidate candidate : block) {
            if (alreadyPaid.contains(candidate.cpf())) {
                tally.skipAlreadyPaid();
                continue;
            }

            BenefitInput input = new BenefitInput(
                    candidate.cpf(),
                    referencePeriod,
                    candidate.regionCode(),
                    candidate.familyIncome().orElse(BigDecimal.ZERO),
                    candidate.activeDependents(),
                    candidate.age().orElse(0));

            EligibilityResult result = eligibility.check(input, candidate.status(), program);
            if (!result.eligible()) {
                tally.reject(candidate.cpf(), result.reasons());
                continue;
            }
            BenefitCalculation calculation = calculator.calculate(input, program);
            generated.add(Payment.generate(
                    candidate.cpf(),
                    program.code(),
                    referencePeriod,
                    cycleId,
                    calculation,
                    actor,
                    clock));
            tally.accumulate(calculation);
        }

        // Ponto unico de gravacao do bloco.
        payments.saveAll(generated);
        publishAudit(generated, cycleId, actor);
    }

    private void publishAudit(List<Payment> generated, String cycleId, Actor actor) {
        if (generated.isEmpty()) {
            return;
        }
        List<AuditableEvent> events = new ArrayList<>(generated.size());
        for (Payment payment : generated) {
            events.add(new PaymentGenerated(
                    String.valueOf(payment.id()),
                    payment.cpf(),
                    payment.referencePeriod(),
                    payment.amountGross(),
                    payment.amountNet(),
                    payment.appliedFactors(),
                    cycleId,
                    actor,
                    payment.generatedAt()));
        }
        publisher.publishEvent(new AuditableEventBatch(events));
    }
}
