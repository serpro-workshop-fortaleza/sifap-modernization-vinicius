package br.gov.sifap.payment.internal;

import br.gov.sifap.payment.BankRemittance;
import br.gov.sifap.payment.BankRemittance.RemittanceLine;
import br.gov.sifap.payment.PaymentStatus;
import br.gov.sifap.payment.event.BankRemittanceIssued;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.exception.DomainRuleException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emissao da remessa bancaria.
 *
 * <p>Atende {@code REQ-PAY-024} e {@code REQ-PAY-025}.
 *
 * <p>A remessa le do pagamento, e nao de variaveis de trabalho. Essa e a diferenca: o
 * {@code BATCHPGT.NSP:520} monta a linha a partir de {@code #AMT-NET} em memoria, que no
 * caminho do {@code CALCDSCT} ficou defasado do que o registro declara.
 */
@Service
public class BankRemittanceService {

    private final PaymentRepository payments;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    BankRemittanceService(
            PaymentRepository payments, ApplicationEventPublisher publisher, Clock clock) {
        this.payments = payments;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public BankRemittance issue(String cycleId, String referencePeriod, Actor actor) {
        List<Payment> generated = payments.findByReferencePeriodOrderByCpf(referencePeriod).stream()
                .filter(payment -> payment.cycleId().equals(cycleId))
                .toList();

        if (generated.isEmpty()) {
            throw new DomainRuleException("REQ-PAY-024", "ciclo sem pagamentos para remessa: " + cycleId);
        }

        List<Payment> pending = generated.stream()
                .filter(payment -> payment.status() == PaymentStatus.GERADO)
                .toList();

        if (pending.size() != generated.size()) {
            throw new DomainRuleException(
                    "REQ-PAY-024", "remessa ja emitida para o ciclo " + cycleId);
        }

        List<RemittanceLine> lines = pending.stream()
                .map(payment -> new RemittanceLine(
                        String.valueOf(payment.id()), payment.cpf(), payment.amountNet()))
                .toList();

        BigDecimal controlTotal = lines.stream()
                .map(RemittanceLine::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // REQ-PAY-025: a emissao muda o estado. O legado nao marca nada, e reexecutar o
        // job envia a mesma folha ao banco de novo.
        pending.forEach(Payment::markIssued);
        payments.saveAll(pending);

        publisher.publishEvent(new BankRemittanceIssued(
                cycleId, referencePeriod, lines.size(), controlTotal, actor, clock.instant()));

        return new BankRemittance(cycleId, referencePeriod, lines.size(), controlTotal, lines);
    }
}
