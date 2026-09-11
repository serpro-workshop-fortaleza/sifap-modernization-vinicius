package br.gov.sifap.payment;

import java.math.BigDecimal;
import java.util.List;

/**
 * Remessa bancaria de um ciclo.
 *
 * <p>Atende {@code REQ-PAY-024} e {@code REQ-PAY-025}. O legado grava o arquivo em
 * {@code BATCHPGT.NSP:520} a partir de campos que, no caminho do {@code CALCDSCT}, ainda
 * carregam o liquido anterior ao desconto: o valor que chega ao banco nao e o valor que o
 * pagamento declara.
 *
 * @param controlTotal soma dos liquidos, conferida contra a folha antes do envio
 */
public record BankRemittance(
        String cycleId,
        String referencePeriod,
        int records,
        BigDecimal controlTotal,
        List<RemittanceLine> lines) {

    public BankRemittance {
        lines = List.copyOf(lines);
    }

    public record RemittanceLine(String paymentId, String cpf, BigDecimal netAmount) {
    }
}
