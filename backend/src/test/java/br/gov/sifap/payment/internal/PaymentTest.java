package br.gov.sifap.payment.internal;

import static br.gov.sifap.payment.ProgramFixture.aProgram;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.payment.BenefitCalculation;
import br.gov.sifap.payment.BenefitInput;
import br.gov.sifap.payment.DiscountType;
import br.gov.sifap.payment.PaymentStatus;
import br.gov.sifap.payment.internal.calculation.BenefitCalculator;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.exception.DomainRuleException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Agregado de pagamento")
class PaymentTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-03-10T12:00:00Z"), ZoneOffset.UTC);
    private static final Actor ACTOR = Actor.process("BATCHPGT");

    private static Payment payment(String amountBase) {
        BenefitCalculation calculation = new BenefitCalculator()
                .calculate(
                        new BenefitInput("11144477735", "202503", "01", new BigDecimal("100.00"), 0, 30),
                        aProgram().withAmountBase(amountBase).build());
        return Payment.generate("11144477735", "0001", "202503", "0001-202503", calculation, ACTOR, CLOCK);
    }

    private static PaymentDiscount discount(DiscountType type, String amount) {
        return new PaymentDiscount(
                1L, type, new BigDecimal(amount), null, type == DiscountType.JUDICIAL ? "0001234-55" : null);
    }

    @Test
    @DisplayName("deve recalcular o liquido quando os descontos sao aplicados")
    void deve_recalcular_o_liquido_quando_os_descontos_sao_aplicados() {
        Payment payment = payment("1000.00");
        BigDecimal gross = payment.amountGross();

        payment.applyDiscounts(List.of(discount(DiscountType.CONSIGNADO, "100.00")));

        // REQ-PAY-020: CALCDSCT.NSP:188 atualiza o total de descontos e encerra a
        // transacao sem tocar no liquido que segue para o banco.
        assertThat(payment.amountNet()).isEqualByComparingTo(gross.subtract(payment.amountDiscount()));
    }

    /**
     * {@code REQ-PAY-019}. {@code CALCDSCT.NSP:170-175} corta o acumulado de todos os tipos
     * dentro do laco, de modo que um desconto comum processado depois de um judicial reduz
     * o judicial — que {@code :132} declara isento de teto.
     */
    @Test
    @DisplayName("deve preservar o desconto judicial quando o teto corta os demais")
    void deve_preservar_o_desconto_judicial_quando_o_teto_corta_os_demais() {
        Payment payment = payment("1000.00");
        BigDecimal ceiling = payment.amountGross().multiply(new BigDecimal("0.30"));

        payment.applyDiscounts(List.of(
                discount(DiscountType.JUDICIAL, "400.00"), discount(DiscountType.CONSIGNADO, "500.00")));

        BigDecimal expected = payment.amountSocial().add(new BigDecimal("400.00")).add(ceiling);
        assertThat(payment.amountDiscount()).isEqualByComparingTo(expected.setScale(2, java.math.RoundingMode.DOWN));
    }

    @Test
    @DisplayName("deve chegar ao mesmo total quando a ordem dos descontos muda")
    void deve_chegar_ao_mesmo_total_quando_a_ordem_dos_descontos_muda() {
        List<PaymentDiscount> ordered =
                List.of(discount(DiscountType.JUDICIAL, "300.00"), discount(DiscountType.TAXA, "200.00"));
        List<PaymentDiscount> reversed = ordered.reversed();

        Payment first = payment("1000.00");
        Payment second = payment("1000.00");
        first.applyDiscounts(ordered);
        second.applyDiscounts(reversed);

        assertThat(first.amountDiscount()).isEqualByComparingTo(second.amountDiscount());
        assertThat(first.amountNet()).isEqualByComparingTo(second.amountNet());
    }

    @Test
    @DisplayName("deve recusar os descontos quando superam o valor bruto")
    void deve_recusar_os_descontos_quando_superam_o_valor_bruto() {
        Payment payment = payment("100.00");

        assertThatThrownBy(() -> payment.applyDiscounts(List.of(discount(DiscountType.JUDICIAL, "500.00"))))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("superam");
    }

    @Test
    @DisplayName("deve registrar o indice e o periodo quando a correcao e aplicada")
    void deve_registrar_o_indice_e_o_periodo_quando_a_correcao_e_aplicada() {
        Payment payment = payment("1000.00");

        payment.applyCorrection(new BigDecimal("1.050000"), "202502", CLOCK);

        assertThat(payment.correctionIndex()).contains(new BigDecimal("1.050000"));
        assertThat(payment.correctedAt()).isPresent();
        assertThat(payment.amountCorrection()).isPresent();
    }

    @Test
    @DisplayName("deve recusar a segunda correcao quando o pagamento ja foi corrigido")
    void deve_recusar_a_segunda_correcao_quando_o_pagamento_ja_foi_corrigido() {
        Payment payment = payment("1000.00");
        payment.applyCorrection(new BigDecimal("1.050000"), "202502", CLOCK);

        assertThatThrownBy(() -> payment.applyCorrection(new BigDecimal("1.100000"), "202502", CLOCK))
                .isInstanceOf(DomainRuleException.class);
    }

    @Test
    @DisplayName("deve ignorar a correcao quando o indice nao aumenta o valor")
    void deve_ignorar_a_correcao_quando_o_indice_nao_aumenta_o_valor() {
        Payment payment = payment("1000.00");

        payment.applyCorrection(BigDecimal.ONE, "202502", CLOCK);

        assertThat(payment.amountCorrection()).isEmpty();
        assertThat(payment.correctedAt()).isEmpty();
    }

    @Test
    @DisplayName("deve recusar a remessa quando o pagamento ja foi emitido")
    void deve_recusar_a_remessa_quando_o_pagamento_ja_foi_emitido() {
        Payment payment = payment("1000.00");
        payment.markIssued();

        assertThat(payment.status()).isEqualTo(PaymentStatus.EMITIDO);
        assertThatThrownBy(payment::markIssued).isInstanceOf(DomainRuleException.class);
    }
}
