package br.gov.sifap.payment.internal.reconciliation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Posicoes do layout do arquivo de retorno.
 *
 * <p>Em configuracao, e nao em constante, porque o layout varia entre bancos. O
 * {@code BATCHCON} tem as posicoes do Banco do Brasil no corpo do programa e um bloco
 * comentado com as do Banco Real, adquirido em 2007 ({@code BATCHCON.NSP:245-262}):
 * suportar um segundo banco exigiu duplicar o laco de leitura inteiro.
 *
 * <p>Os valores padrao sao os de {@code BATCHCON.NSP:150-164}, em base 1 como no Natural.
 */
@ConfigurationProperties(prefix = "sifap.reconciliation.layout")
public record ReturnFileLayout(
        int recordLength,
        Field bank,
        Field recordType,
        Field cpf,
        Field amount,
        Field paymentDate,
        Field returnCode,
        Field documentNumber,
        String detailRecordType) {

    public ReturnFileLayout {
        recordLength = recordLength == 0 ? 240 : recordLength;
        bank = bank == null ? new Field(1, 3) : bank;
        recordType = recordType == null ? new Field(8, 1) : recordType;
        cpf = cpf == null ? new Field(44, 11) : cpf;
        amount = amount == null ? new Field(120, 15) : amount;
        paymentDate = paymentDate == null ? new Field(140, 8) : paymentDate;
        returnCode = returnCode == null ? new Field(231, 2) : returnCode;
        documentNumber = documentNumber == null ? new Field(74, 10) : documentNumber;
        detailRecordType = detailRecordType == null ? "3" : detailRecordType;
    }

    public static ReturnFileLayout cnab240() {
        return new ReturnFileLayout(0, null, null, null, null, null, null, null, null);
    }

    /** @param start posicao inicial em base 1, como no Natural */
    public record Field(int start, int length) {

        public String from(String line) {
            int begin = start - 1;
            if (begin >= line.length()) {
                return "";
            }
            return line.substring(begin, Math.min(begin + length, line.length())).trim();
        }
    }
}
