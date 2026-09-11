package br.gov.sifap.payment.internal;

import br.gov.sifap.payment.BenefitCalculation;
import br.gov.sifap.payment.DiscountType;
import br.gov.sifap.payment.FactorType;
import br.gov.sifap.payment.PaymentStatus;
import br.gov.sifap.payment.PaymentType;
import br.gov.sifap.payment.internal.calculation.MonetaryScale;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.exception.DomainRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Raiz do agregado de pagamento.
 *
 * <p>Atende {@code REQ-PAY-002}, {@code REQ-PAY-016} e {@code REQ-PAY-020}.
 *
 * <p>O numero vem de sequencia do banco. O legado usa um contador de memoria
 * ({@code BATCHPGT.NSP:473}) e, no caminho do {@code CALCBENF}, nao atribui numero nenhum —
 * sobre um campo que {@code PAYMENT.ddm:29} declara unico.
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "paymentSeq")
    @SequenceGenerator(name = "paymentSeq", sequenceName = "payment_id_seq", allocationSize = 500)
    private Long id;

    @Column(name = "cpf", nullable = false, updatable = false, length = 11)
    private String cpf;

    @Column(name = "program_code", nullable = false, updatable = false, length = 4)
    private String programCode;

    @Column(name = "reference_period", nullable = false, updatable = false, length = 6)
    private String referencePeriod;

    @Column(name = "cycle_id", nullable = false, updatable = false, length = 20)
    private String cycleId;

    @Column(name = "amount_base", nullable = false, precision = 11, scale = 2)
    private BigDecimal amountBase;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applied_factors", nullable = false)
    private Map<FactorType, BigDecimal> appliedFactors;

    @Column(name = "amount_gross", nullable = false, precision = 11, scale = 2)
    private BigDecimal amountGross;

    @Column(name = "amount_thirteenth", nullable = false, precision = 11, scale = 2)
    private BigDecimal amountThirteenth;

    @Column(name = "amount_bonus", nullable = false, precision = 11, scale = 2)
    private BigDecimal amountBonus;

    /** Contribuicao social; parcela do total que nao depende de lancamento externo. */
    @Column(name = "amount_social", nullable = false, precision = 11, scale = 2)
    private BigDecimal amountSocial;

    @Column(name = "amount_discount", nullable = false, precision = 11, scale = 2)
    private BigDecimal amountDiscount;

    @Column(name = "amount_net", nullable = false, precision = 11, scale = 2)
    private BigDecimal amountNet;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 14)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 12)
    private PaymentType type;

    @Column(name = "amount_correction", precision = 11, scale = 2)
    private BigDecimal amountCorrection;

    @Column(name = "correction_index", precision = 11, scale = 6)
    private BigDecimal correctionIndex;

    @Column(name = "correction_period", length = 6)
    private String correctionPeriod;

    @Column(name = "corrected_at")
    private LocalDate correctedAt;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "generated_by", nullable = false, updatable = false, length = 50)
    private String generatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Descontos carregados sob demanda; a tabela nao e particionada junto do pagamento. */
    @Transient
    private final java.util.List<PaymentDiscount> discounts = new ArrayList<>();

    protected Payment() {
        // exigido pelo JPA
    }

    public static Payment generate(
            String cpf,
            String programCode,
            String referencePeriod,
            String cycleId,
            BenefitCalculation calculation,
            Actor actor,
            Clock clock) {
        Objects.requireNonNull(calculation, "calculation");

        Payment payment = new Payment();
        payment.cpf = cpf;
        payment.programCode = programCode;
        payment.referencePeriod = referencePeriod;
        payment.cycleId = cycleId;
        payment.amountBase = calculation.amountBase();
        payment.appliedFactors = Map.copyOf(calculation.appliedFactors());
        payment.amountGross = calculation.grossAmount();
        payment.amountThirteenth = calculation.thirteenthAmount();
        payment.amountBonus = calculation.bonusAmount();
        payment.amountSocial = calculation.discountAmount();
        payment.amountDiscount = calculation.discountAmount();
        payment.amountNet = calculation.netAmount();
        payment.status = PaymentStatus.GERADO;
        // REQ-PAY-016: dezembro nao muda o tipo. O decimo terceiro e o abono sao parcelas
        // deste pagamento, com valores em campos proprios.
        payment.type = PaymentType.NORMAL;
        payment.generatedAt = clock.instant();
        payment.generatedBy = actor.id();
        return payment;
    }

    /**
     * Substitui os descontos e recalcula o liquido.
     *
     * <p>Atende {@code REQ-PAY-019} e {@code REQ-PAY-020}. O {@code CALCDSCT} atualiza
     * {@code AMT-DISC-TOTAL} e encerra a transacao sem tocar em {@code AMT-NET} — que e o
     * valor que segue para o extrato bancario.
     */
    public void applyDiscounts(java.util.List<PaymentDiscount> applied) {
        this.discounts.clear();
        this.discounts.addAll(applied);

        BigDecimal total = amountSocial.add(DiscountCap.apply(applied, amountGross));
        BigDecimal net = MonetaryScale.truncate(amountGross.subtract(total));
        if (net.signum() < 0) {
            throw new DomainRuleException(
                    "REQ-PAY-019", "descontos superam o valor bruto do pagamento");
        }

        this.amountDiscount = total;
        this.amountNet = net;
    }

    /**
     * Registra a correcao retroativa.
     *
     * <p>Atende {@code REQ-PAY-021}: o indice aplicado e o periodo coberto ficam registrados,
     * o que o legado nao faz.
     */
    public void applyCorrection(BigDecimal index, String period, Clock clock) {
        if (this.correctedAt != null) {
            throw new DomainRuleException("REQ-PAY-021", "pagamento ja corrigido");
        }
        BigDecimal corrected = MonetaryScale.truncate(amountGross.multiply(index));
        if (corrected.compareTo(amountGross) <= 0) {
            return;
        }
        this.amountCorrection = corrected;
        this.correctionIndex = index;
        this.correctionPeriod = period;
        this.correctedAt = LocalDate.now(clock);
    }

    /**
     * Reconstroi um pagamento vindo do historico legado.
     *
     * <p>Atende {@code REQ-PAY-016}. <strong>Nao recalcula nada.</strong> Recalcular 180
     * milhoes de pagamentos com a formula corrigida mudaria valores ja pagos, e pagamento
     * feito e fato consumado — a correcao vale para as folhas futuras.
     *
     * <p>Os fatores aplicados ficam vazios porque o legado nao os registra: e a ausencia
     * que o {@code AC-010.2} passa a evitar daqui para frente.
     *
     * <p>O liquido nao e um parametro. Ele deriva de bruto menos desconto, e o valor da
     * origem — que o {@code CALCDSCT.NSP:188} deixa defasado — fica no relatorio de carga.
     */
    public static Payment migrated(
            String cpf,
            String programCode,
            String referencePeriod,
            String cycleId,
            BigDecimal amountGross,
            BigDecimal amountDiscount,
            PaymentStatus status,
            PaymentType type,
            Actor actor,
            Clock clock) {

        Payment payment = new Payment();
        payment.cpf = cpf;
        payment.programCode = programCode;
        payment.referencePeriod = referencePeriod;
        payment.cycleId = cycleId;
        payment.amountBase = amountGross;
        payment.appliedFactors = Map.of();
        payment.amountGross = amountGross;
        payment.amountThirteenth = BigDecimal.ZERO;
        payment.amountBonus = BigDecimal.ZERO;
        payment.amountSocial = BigDecimal.ZERO;
        payment.amountDiscount = amountDiscount;
        // A restricao ck_payment_net_consistent nao aceita o liquido defasado que o
        // CALCDSCT deixa; o valor da origem fica no relatorio de carga.
        payment.amountNet = MonetaryScale.truncate(amountGross.subtract(amountDiscount));        payment.status = status;
        payment.type = type;
        payment.generatedAt = clock.instant();
        payment.generatedBy = actor.id();
        return payment;
    }

    /**
     * Marca o pagamento como enviado ao banco.
     *
     * <p>Atende {@code REQ-PAY-025}. Sem esta marca, reexecutar a remessa envia a mesma
     * folha ao banco de novo — o legado nao registra a emissao em lugar nenhum.
     */
    public void markIssued() {
        if (status != PaymentStatus.GERADO) {
            throw new DomainRuleException(
                    "REQ-PAY-025", "pagamento em situacao " + status + " nao pode ser remetido");
        }
        this.status = PaymentStatus.EMITIDO;
    }

    public boolean hasDiscountOf(DiscountType type) {
        return discounts.stream().anyMatch(discount -> discount.type() == type);
    }

    public Long id() {
        return id;
    }

    public String cpf() {
        return cpf;
    }

    public String programCode() {
        return programCode;
    }

    public String referencePeriod() {
        return referencePeriod;
    }

    public String cycleId() {
        return cycleId;
    }

    public BigDecimal amountBase() {
        return amountBase;
    }

    public Map<FactorType, BigDecimal> appliedFactors() {
        return Map.copyOf(appliedFactors);
    }

    public BigDecimal amountGross() {
        return amountGross;
    }

    public BigDecimal amountThirteenth() {
        return amountThirteenth;
    }

    public BigDecimal amountBonus() {
        return amountBonus;
    }

    public BigDecimal amountDiscount() {
        return amountDiscount;
    }

    public BigDecimal amountSocial() {
        return amountSocial;
    }

    public BigDecimal amountNet() {
        return amountNet;
    }

    public PaymentStatus status() {
        return status;
    }

    public PaymentType type() {
        return type;
    }

    public Optional<BigDecimal> amountCorrection() {
        return Optional.ofNullable(amountCorrection);
    }

    public Optional<BigDecimal> correctionIndex() {
        return Optional.ofNullable(correctionIndex);
    }

    public Optional<LocalDate> correctedAt() {
        return Optional.ofNullable(correctedAt);
    }

    public String generatedBy() {
        return generatedBy;
    }

    public Instant generatedAt() {
        return generatedAt;
    }
}
