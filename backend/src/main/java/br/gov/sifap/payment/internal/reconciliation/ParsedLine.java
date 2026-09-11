package br.gov.sifap.payment.internal.reconciliation;

import java.util.List;

/**
 * Resultado da leitura de uma linha do arquivo de retorno.
 *
 * <p>Interface selada em vez de valor nulo ou excecao: um registro ilegivel nao interrompe
 * o arquivo nem some. O {@code BATCHCON} nao tem terceira possibilidade — ou o
 * {@code SUBSTR} funciona, ou o {@code VAL} devolve zero em silencio.
 */
sealed interface ParsedLine {

    /** Cabecalho, rodape ou qualquer registro que nao seja de detalhe. */
    record Skipped(int lineNumber) implements ParsedLine {
    }

    record Parsed(ReturnRecord record) implements ParsedLine {
    }

    /** @param reasons tudo que impediu a leitura, nao apenas o primeiro problema */
    record Rejected(int lineNumber, String cpf, List<String> reasons) implements ParsedLine {
        public Rejected {
            reasons = List.copyOf(reasons);
        }
    }
}
