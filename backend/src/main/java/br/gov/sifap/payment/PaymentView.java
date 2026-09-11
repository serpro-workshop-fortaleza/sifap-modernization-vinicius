package br.gov.sifap.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Projecao de leitura de um pagamento. */
public record PaymentView(
        String paymentId,
        String maskedCpf,
        String programCode,
        String referencePeriod,
        BigDecimal amountBase,
        BigDecimal amountGross,
        BigDecimal amountThirteenth,
        BigDecimal amountBonus,
        BigDecimal amountSocial,
        BigDecimal amountDiscount,
        BigDecimal amountNet,
        PaymentStatus status,
        PaymentType type,
        Optional<BigDecimal> amountCorrection,
        Optional<BigDecimal> correctionIndex,
        Optional<LocalDate> correctedAt,
        Optional<ReconciliationStatus> reconciliationStatus,
        Optional<BigDecimal> amountReconciled,
        Optional<LocalDate> creditDate,
        Optional<String> bankCode,
        List<AppliedFactorView> appliedFactors,
        List<DiscountView> discounts) {

    public PaymentView {
        appliedFactors = List.copyOf(appliedFactors);
        discounts = List.copyOf(discounts);
    }

    /**
     * Fator aplicado ao calculo.
     *
     * <p>Atende {@code AC-010.2}: sem isto, a divergencia entre a formula multiplicativa do
     * codigo e a formula aditiva da {@code RN-013} so aparece para quem le Natural.
     */
    public record AppliedFactorView(FactorType type, BigDecimal value) {
    }

    public record DiscountView(
            DiscountType type, BigDecimal amount, boolean exemptFromCap, Optional<String> caseNumber) {
    }
}
