package br.gov.sifap.payment.internal;

import br.gov.sifap.payment.PaymentView;
import br.gov.sifap.payment.PaymentView.AppliedFactorView;
import br.gov.sifap.payment.PaymentView.DiscountView;
import br.gov.sifap.shared.document.Cpf;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Conversao do agregado para a projecao publica. */
final class PaymentMapper {

    private PaymentMapper() {
    }

    static PaymentView toView(Payment payment, List<PaymentDiscount> discounts) {
        return new PaymentView(
                String.valueOf(payment.id()),
                Cpf.mask(payment.cpf()),
                payment.programCode(),
                payment.referencePeriod(),
                payment.amountBase(),
                payment.amountGross(),
                payment.amountThirteenth(),
                payment.amountBonus(),
                payment.amountSocial(),
                payment.amountDiscount(),
                payment.amountNet(),
                payment.status(),
                payment.type(),
                payment.amountCorrection(),
                payment.correctionIndex(),
                payment.correctedAt(),
                payment.reconciliationStatus(),
                payment.amountReconciled(),
                payment.creditDate(),
                payment.bankCode(),
                factorsOf(payment.appliedFactors()),
                discountsOf(discounts));
    }

    private static List<AppliedFactorView> factorsOf(
            Map<br.gov.sifap.payment.FactorType, java.math.BigDecimal> factors) {
        return factors.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new AppliedFactorView(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<DiscountView> discountsOf(List<PaymentDiscount> discounts) {
        return discounts.stream()
                .sorted(Comparator.comparing(discount -> discount.type().name()))
                .map(discount -> new DiscountView(
                        discount.type(),
                        discount.amount(),
                        discount.isExemptFromCap(),
                        discount.caseNumber()))
                .toList();
    }
}
