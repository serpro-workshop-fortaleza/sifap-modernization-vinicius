package br.gov.sifap.payment.internal.migration;

import br.gov.sifap.payment.PaymentStatus;
import br.gov.sifap.payment.PaymentType;
import br.gov.sifap.payment.internal.Payment;
import br.gov.sifap.payment.internal.PaymentRepository;
import br.gov.sifap.shared.event.Actor;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga do historico de pagamentos, com inventario dos defeitos conhecidos.
 *
 * <p>Atende {@code REQ-PAY-016}.
 *
 * <p><strong>Nenhum valor e recalculado.</strong> Pagamento feito e fato consumado; a
 * formula corrigida vale para as folhas seguintes. O que a carga faz e tornar contavel o
 * que hoje nao e: quantos registros nao tem numero, quantos CPFs tem mais de um pagamento
 * no mesmo periodo e em quantos o liquido nao fecha com bruto menos desconto.
 *
 * <p>O duplicado nao entra: a restricao {@code uq_payment_cpf_period} o impede, e decidir
 * qual dos dois vale exige a conciliacao bancaria da Fatia 5. Ate la, o registro fica no
 * relatorio — visivel, e nao silenciosamente escolhido.
 */
@Component
public class PaymentLoader {

    private final PaymentRepository repository;
    private final Clock clock;

    public PaymentLoader(PaymentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public PaymentMigrationReport load(List<String> lines, Actor actor) {
        Map<String, Long> typeOutOfDomain = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        int read = 0;
        int migrated = 0;
        int withoutNumber = 0;
        int duplicated = 0;
        int inconsistentNet = 0;

        for (String line : lines) {
            Optional<LegacyPaymentRecord> parsed = LegacyPaymentRecord.parse(line);
            if (parsed.isEmpty()) {
                continue;
            }
            LegacyPaymentRecord record = parsed.get();
            read++;

            if (!record.hasNumber()) {
                withoutNumber++;
            }

            String key = record.cpf() + "-" + record.referencePeriod();
            if (!seen.add(key)) {
                duplicated++;
                continue;
            }

            BigDecimal gross = amount(record.amountGross());
            BigDecimal discount = amount(record.amountDiscount());
            BigDecimal net = amount(record.amountNet());
            if (net.compareTo(gross.subtract(discount)) != 0) {
                inconsistentNet++;
            }

            PaymentType type = PaymentType.fromLegacyCode(record.type()).orElse(null);
            if (type == null) {
                count(typeOutOfDomain, "type=" + blankAsMarker(record.type()));
                type = PaymentType.NORMAL;
            }

            PaymentStatus status =
                    PaymentStatus.fromLegacyCode(record.status()).orElse(PaymentStatus.CONFIRMADO);

            repository.save(Payment.migrated(
                    record.cpf(),
                    record.programCode(),
                    record.referencePeriod(),
                    "MIGRACAO-" + record.referencePeriod(),
                    gross,
                    discount,
                    status,
                    type,
                    actor,
                    clock));
            migrated++;
        }

        return new PaymentMigrationReport(
                read, migrated, withoutNumber, duplicated, inconsistentNet, typeOutOfDomain);
    }

    private static void count(Map<String, Long> counters, String key) {
        counters.merge(key, 1L, Long::sum);
    }

    private static String blankAsMarker(String value) {
        return value.isBlank() ? "(em branco)" : value;
    }

    /** Valor legado vem sem separador decimal, com duas casas implicitas. */
    private static BigDecimal amount(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim()).movePointLeft(2);
        } catch (NumberFormatException invalid) {
            return BigDecimal.ZERO;
        }
    }
}
