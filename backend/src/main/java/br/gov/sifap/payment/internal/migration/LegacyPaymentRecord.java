package br.gov.sifap.payment.internal.migration;

import java.util.Optional;

/**
 * Linha do arquivo de extracao do histórico de pagamentos.
 *
 * <p>Campos posicionais, na ordem de {@code PAYMENT.ddm}. Valores monetarios vem sem
 * separador decimal, com duas casas implicitas, como no Adabas.
 */
public record LegacyPaymentRecord(
        String paymentNumber,
        String cpf,
        String programCode,
        String referencePeriod,
        String amountGross,
        String amountDiscount,
        String amountNet,
        String status,
        String type) {

    private static final int FIELDS = 9;

    public static Optional<LegacyPaymentRecord> parse(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String[] parts = line.split(";", -1);
        if (parts.length < FIELDS) {
            return Optional.empty();
        }
        return Optional.of(new LegacyPaymentRecord(
                parts[0].trim(),
                parts[1].trim(),
                parts[2].trim(),
                parts[3].trim(),
                parts[4].trim(),
                parts[5].trim(),
                parts[6].trim(),
                parts[7].trim(),
                parts[8].trim()));
    }

    public boolean hasNumber() {
        return !paymentNumber.isBlank();
    }
}
