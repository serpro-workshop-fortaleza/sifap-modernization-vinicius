package br.gov.sifap.payment.internal.reconciliation;

import br.gov.sifap.payment.internal.reconciliation.ReturnFileLayout.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Leitura posicional do arquivo de retorno.
 *
 * <p>Atende {@code REQ-REC-004} e {@code REQ-REC-005}.
 *
 * <p>O valor vem em centavos e e dividido por cem. O comentario de
 * {@code BATCHCON.NSP:166-168} registra que <strong>ate 2017</strong> o campo alfanumerico
 * de quinze posicoes ia direto para o campo decimal, sem divisao: conciliacoes anteriores
 * ao ticket 8112/2017 compararam valores em escalas diferentes.
 */
final class ReturnFileParser {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int CENTS_SCALE = 2;

    private final ReturnFileLayout layout;

    ReturnFileParser(ReturnFileLayout layout) {
        this.layout = layout;
    }

    ParsedLine parse(String line, int lineNumber) {
        if (line == null || line.isBlank()) {
            return new ParsedLine.Skipped(lineNumber);
        }
        if (!layout.detailRecordType().equals(layout.recordType().from(line))) {
            return new ParsedLine.Skipped(lineNumber);
        }

        String cpf = layout.cpf().from(line);
        List<String> reasons = new ArrayList<>();

        BigDecimal amount = amountOf(layout.amount(), line, reasons);
        LocalDate paymentDate = dateOf(layout.paymentDate(), line, reasons);
        String bankCode = layout.bank().from(line);
        String returnCode = layout.returnCode().from(line);

        if (cpf.isBlank()) {
            reasons.add("CPF ausente no registro de retorno");
        }
        if (bankCode.isBlank()) {
            reasons.add("codigo do banco ausente no registro de retorno");
        }

        if (!reasons.isEmpty()) {
            return new ParsedLine.Rejected(lineNumber, cpf, reasons);
        }

        return new ParsedLine.Parsed(new ReturnRecord(
                lineNumber,
                bankCode,
                cpf,
                paymentNumberOf(line),
                amount,
                paymentDate,
                returnCode));
    }

    /** Numero zerado ou em branco e ausencia, nao identificador. */
    private Optional<String> paymentNumberOf(String line) {
        String raw = layout.documentNumber().from(line);
        if (raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.replaceFirst("^0+", "");
        return normalized.isBlank() ? Optional.empty() : Optional.of(normalized);
    }

    private static BigDecimal amountOf(Field field, String line, List<String> reasons) {
        String raw = field.from(line);
        if (raw.isBlank()) {
            reasons.add("valor ausente no registro de retorno");
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw).movePointLeft(CENTS_SCALE);
        } catch (NumberFormatException invalid) {
            reasons.add("valor nao numerico no registro de retorno: " + raw);
            return BigDecimal.ZERO;
        }
    }

    private static LocalDate dateOf(Field field, String line, List<String> reasons) {
        String raw = field.from(line);
        if (raw.isBlank()) {
            reasons.add("data de pagamento ausente no registro de retorno");
            return null;
        }
        try {
            return LocalDate.parse(raw, FILE_DATE);
        } catch (RuntimeException invalid) {
            reasons.add("data de pagamento invalida no registro de retorno: " + raw);
            return null;
        }
    }
}
