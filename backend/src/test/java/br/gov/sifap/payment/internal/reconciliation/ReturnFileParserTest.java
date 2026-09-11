package br.gov.sifap.payment.internal.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Leitura do arquivo de retorno")
class ReturnFileParserTest {

    private final ReturnFileParser parser = new ReturnFileParser(ReturnFileLayout.cnab240());

    /** Monta uma linha CNAB 240 com os campos nas posicoes de BATCHCON.NSP:150-164. */
    private static String line(String type, String cpf, String document, String amount, String date, String code) {
        char[] buffer = new char[240];
        java.util.Arrays.fill(buffer, ' ');
        put(buffer, 1, "001");
        put(buffer, 8, type);
        put(buffer, 44, cpf);
        put(buffer, 74, document);
        put(buffer, 120, amount);
        put(buffer, 140, date);
        put(buffer, 231, code);
        return new String(buffer);
    }

    private static void put(char[] buffer, int start, String value) {
        value.getChars(0, value.length(), buffer, start - 1);
    }

    @Test
    @DisplayName("deve converter o valor de centavos quando o registro e de detalhe")
    void deve_converter_o_valor_de_centavos_quando_o_registro_e_de_detalhe() {
        ParsedLine result = parser.parse(
                line("3", "11144477735", "0000001234", "000000000100000", "20250310", "00"), 1);

        assertThat(result).isInstanceOf(ParsedLine.Parsed.class);
        ReturnRecord record = ((ParsedLine.Parsed) result).record();
        assertThat(record.amount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(record.paymentDate()).isEqualTo(LocalDate.of(2025, 3, 10));
        assertThat(record.cpf()).isEqualTo("11144477735");
        assertThat(record.paymentNumber()).contains("1234");
    }

    @Test
    @DisplayName("deve ignorar o registro quando nao e de detalhe")
    void deve_ignorar_o_registro_quando_nao_e_de_detalhe() {
        assertThat(parser.parse(line("0", "", "", "", "", ""), 1)).isInstanceOf(ParsedLine.Skipped.class);
        assertThat(parser.parse(line("9", "", "", "", "", ""), 2)).isInstanceOf(ParsedLine.Skipped.class);
    }

    @Test
    @DisplayName("deve recusar o registro quando o valor nao e numerico")
    void deve_recusar_o_registro_quando_o_valor_nao_e_numerico() {
        ParsedLine result = parser.parse(
                line("3", "11144477735", "0000001234", "ABCDEFGHIJKLMNO", "20250310", "00"), 1);

        assertThat(result).isInstanceOf(ParsedLine.Rejected.class);
        assertThat(((ParsedLine.Rejected) result).reasons()).anyMatch(r -> r.contains("nao numerico"));
    }

    /** O legado devolve zero em silencio quando o VAL falha; aqui o registro vira pendencia. */
    @Test
    @DisplayName("deve devolver todos os motivos quando mais de um campo esta ilegivel")
    void deve_devolver_todos_os_motivos_quando_mais_de_um_campo_esta_ilegivel() {
        ParsedLine result = parser.parse(line("3", "", "0000001234", "XX", "99999999", "00"), 1);

        assertThat(result).isInstanceOf(ParsedLine.Rejected.class);
        assertThat(((ParsedLine.Rejected) result).reasons()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("deve tratar numero de documento zerado como ausencia")
    void deve_tratar_numero_de_documento_zerado_como_ausencia() {
        ParsedLine result = parser.parse(
                line("3", "11144477735", "0000000000", "000000000100000", "20250310", "00"), 1);

        ReturnRecord record = ((ParsedLine.Parsed) result).record();
        assertThat(record.paymentNumber()).isEmpty();
    }

    @Test
    @DisplayName("deve ignorar a linha em branco sem conta-la como pendencia")
    void deve_ignorar_a_linha_em_branco_sem_conta_la_como_pendencia() {
        assertThat(parser.parse("   ", 1)).isInstanceOf(ParsedLine.Skipped.class);
        assertThat(parser.parse(null, 2)).isInstanceOf(ParsedLine.Skipped.class);
    }
}
