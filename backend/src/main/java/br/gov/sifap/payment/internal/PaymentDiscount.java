package br.gov.sifap.payment.internal;

import br.gov.sifap.payment.DiscountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Desconto aplicado a um pagamento.
 *
 * <p>Atende {@code REQ-PAY-018}. Grupo periodico de ate oito no legado
 * ({@code PAYMENT.ddm:41}), entidade propria aqui, como os dependentes na Fatia 2 e as
 * faixas na Fatia 3.
 */
@Entity
@Table(name = "payment_discount")
public class PaymentDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "paymentDiscountSeq")
    @SequenceGenerator(
            name = "paymentDiscountSeq",
            sequenceName = "payment_discount_id_seq",
            allocationSize = 500)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private DiscountType type;

    @Column(name = "amount", nullable = false, precision = 11, scale = 2)
    private BigDecimal amount;

    @Column(name = "percentage", precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "case_number", length = 20)
    private String caseNumber;

    protected PaymentDiscount() {
        // exigido pelo JPA
    }

    PaymentDiscount(Long paymentId, DiscountType type, BigDecimal amount, BigDecimal percentage, String caseNumber) {
        this.paymentId = paymentId;
        this.type = type;
        this.amount = amount;
        this.percentage = percentage;
        this.caseNumber = caseNumber;
    }

    public DiscountType type() {
        return type;
    }

    public BigDecimal amount() {
        return amount;
    }

    public boolean isExemptFromCap() {
        return type.isExemptFromCap();
    }

    public Optional<String> caseNumber() {
        return Optional.ofNullable(caseNumber);
    }

    public Long paymentId() {
        return paymentId;
    }
}
