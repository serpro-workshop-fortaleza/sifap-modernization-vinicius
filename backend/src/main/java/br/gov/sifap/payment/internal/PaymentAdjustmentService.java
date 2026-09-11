package br.gov.sifap.payment.internal;

import br.gov.sifap.payment.DiscountEntry;
import br.gov.sifap.payment.event.PaymentCorrected;
import br.gov.sifap.payment.event.PaymentDiscountsApplied;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.exception.DomainRuleException;
import br.gov.sifap.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Descontos e correcao retroativa.
 *
 * <p>Atende {@code REQ-PAY-018} a {@code REQ-PAY-021}.
 *
 * <p>Os dois programas de origem alteram valores financeiros de formas diferentes e ambos
 * deixam o pagamento inconsistente: {@code CALCDSCT} atualiza o total de descontos e nao
 * recalcula o liquido; {@code CALCCORR} grava a correcao sem registrar o indice usado.
 * Aqui os dois efeitos acontecem no agregado, em uma transacao, com evento.
 */
@Service
public class PaymentAdjustmentService {

    private final PaymentRepository payments;
    private final PaymentDiscountRepository discounts;
    private final CorrectionIndexRepository indexes;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    PaymentAdjustmentService(
            PaymentRepository payments,
            PaymentDiscountRepository discounts,
            CorrectionIndexRepository indexes,
            ApplicationEventPublisher publisher,
            Clock clock) {
        this.payments = payments;
        this.discounts = discounts;
        this.indexes = indexes;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public void applyDiscounts(String cpf, String referencePeriod, List<DiscountEntry> entries, Actor actor) {
        Payment payment = load(cpf, referencePeriod);
        BigDecimal previous = payment.amountDiscount();

        discounts.deleteByPaymentId(payment.id());
        List<PaymentDiscount> applied = entries.stream()
                .map(entry -> new PaymentDiscount(
                        payment.id(),
                        entry.type(),
                        entry.amount(),
                        entry.percentage(),
                        entry.caseNumber().orElse(null)))
                .toList();

        payment.applyDiscounts(applied);
        discounts.saveAll(applied);
        payments.save(payment);

        publisher.publishEvent(new PaymentDiscountsApplied(
                String.valueOf(payment.id()),
                payment.cpf(),
                previous,
                payment.amountDiscount(),
                payment.amountNet(),
                actor,
                clock.instant()));
    }

    /**
     * Correcao retroativa.
     *
     * <p>{@code REQ-PAY-021}, nivel {@code PS}: o legado acumula indices de um unico mes
     * ({@code CALCCORR.NSP:186}) em vez do periodo entre o pagamento e a correcao. O
     * comportamento e preservado porque altera quanto a pessoa recebe; o que muda e que o
     * indice aplicado e o periodo coberto passam a ficar registrados.
     */
    @Transactional
    public void applyCorrection(String cpf, String referencePeriod, String indexPeriod, Actor actor) {
        Payment payment = load(cpf, referencePeriod);
        BigDecimal original = payment.amountGross();

        CorrectionIndex index = indexes
                .findByPeriod(indexPeriod)
                .orElseThrow(() -> new DomainRuleException(
                        "REQ-PAY-021", "indice de correcao ausente para o periodo " + indexPeriod));

        payment.applyCorrection(index.rate(), indexPeriod, clock);
        payments.save(payment);

        payment.amountCorrection().ifPresent(corrected -> publisher.publishEvent(new PaymentCorrected(
                String.valueOf(payment.id()),
                payment.cpf(),
                original,
                corrected,
                index.rate(),
                indexPeriod,
                actor,
                clock.instant())));
    }

    private Payment load(String cpf, String referencePeriod) {
        return payments
                .findByCpfAndReferencePeriod(cpf, referencePeriod)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REQ-PAY-018", "pagamento nao encontrado para o periodo " + referencePeriod));
    }
}
