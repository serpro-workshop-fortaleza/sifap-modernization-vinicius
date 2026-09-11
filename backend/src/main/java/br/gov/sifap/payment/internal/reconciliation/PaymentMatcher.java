package br.gov.sifap.payment.internal.reconciliation;

import br.gov.sifap.payment.internal.Payment;
import br.gov.sifap.payment.internal.PaymentRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Casamento entre o registro de retorno e o pagamento.
 *
 * <p>Atende {@code REQ-REC-001} e {@code REQ-REC-002}.
 *
 * <p>Dois criterios, em ordem. O segundo existe apenas para o historico migrado: os
 * pagamentos que {@code CALCBENF.NSN:319} grava sem numero nao podem ser encontrados pela
 * busca de {@code BATCHCON.NSP:176}, e por isso nunca foram conciliados. Para pagamentos
 * novos ele nunca dispara, porque o {@code REQ-PAY-002} garante numero a todos.
 */
@Component
class PaymentMatcher {

    private final PaymentRepository payments;

    PaymentMatcher(PaymentRepository payments) {
        this.payments = payments;
    }

    Match match(ReturnRecord record, String referencePeriod) {
        Optional<Payment> byNumber = record.paymentNumber()
                .flatMap(PaymentMatcher::parseId)
                .flatMap(payments::findById)
                .filter(payment -> payment.cpf().equals(record.cpf()))
                .filter(payment -> payment.referencePeriod().equals(referencePeriod));

        if (byNumber.isPresent()) {
            return new Match.Found(byNumber.get());
        }

        List<Payment> byCpf = payments.findByCpfAndReferencePeriod(record.cpf(), referencePeriod)
                .map(List::of)
                .orElseGet(List::of);

        return switch (byCpf.size()) {
            case 0 -> new Match.NotFound();
            case 1 -> new Match.Found(byCpf.get(0));
            default -> new Match.Ambiguous(byCpf.size());
        };
    }

    private static Optional<Long> parseId(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException notNumeric) {
            return Optional.empty();
        }
    }

    sealed interface Match {
        record Found(Payment payment) implements Match {
        }

        record NotFound() implements Match {
        }

        /** Só possivel no historico migrado; o `REQ-PAY-001` a impede daqui para frente. */
        record Ambiguous(int candidates) implements Match {
        }
    }
}
