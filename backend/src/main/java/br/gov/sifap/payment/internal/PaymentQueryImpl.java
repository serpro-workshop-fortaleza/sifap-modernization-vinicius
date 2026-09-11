package br.gov.sifap.payment.internal;

import br.gov.sifap.payment.PaymentQuery;
import br.gov.sifap.payment.PaymentView;
import br.gov.sifap.payment.event.PaymentQueried;
import br.gov.sifap.shared.document.Cpf;
import br.gov.sifap.shared.event.Actor;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura da folha.
 *
 * <p>Como no cadastro, a transacao nao e somente leitura: registrar o acesso e escrita.
 */
@Service
@Transactional
class PaymentQueryImpl implements PaymentQuery {

    private final PaymentRepository payments;
    private final PaymentDiscountRepository discounts;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PaymentQueryImpl(
            PaymentRepository payments,
            PaymentDiscountRepository discounts,
            ApplicationEventPublisher events,
            Clock clock) {
        this.payments = payments;
        this.discounts = discounts;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public Optional<PaymentView> findByCpfAndPeriod(Cpf cpf, String referencePeriod, Actor actor) {
        Optional<Payment> found = payments.findByCpfAndReferencePeriod(cpf.value(), referencePeriod);
        found.ifPresent(payment -> register(payment.cpf(), "consulta de pagamento por periodo", actor));
        return found.map(payment -> PaymentMapper.toView(payment, discounts.findByPaymentId(payment.id())));
    }

    @Override
    public List<PaymentView> findByCpf(Cpf cpf, Actor actor) {
        List<Payment> found = payments.findByCpfOrderByReferencePeriodDesc(cpf.value());
        if (found.isEmpty()) {
            return List.of();
        }
        register(cpf.value(), "extrato de pagamentos", actor);
        return found.stream()
                .map(payment -> PaymentMapper.toView(payment, discounts.findByPaymentId(payment.id())))
                .toList();
    }

    private void register(String cpf, String purpose, Actor actor) {
        events.publishEvent(new PaymentQueried(cpf, purpose, actor, clock.instant()));
    }
}
