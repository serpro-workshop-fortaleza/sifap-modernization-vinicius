package br.gov.sifap.beneficiary;

import java.util.List;

/**
 * Leitura em lote do cadastro para processamento de folha.
 *
 * <p>Separada de {@link BeneficiaryQuery} de proposito. Aquela exige {@code Actor} porque
 * registra cada acesso a dado pessoal ({@code REQ-BEN-016}); esta serve a um processo
 * automatico cujo registro e o evento do ciclo, com {@code batchRunId}. Emitir 3,8 milhoes
 * de eventos de consulta por folha nao aumentaria a prestacao de contas: a auditoria de
 * lote responde por quem processou o que, e o evento de pagamento responde por cada valor.
 *
 * <p>A paginacao e por CPF e nao por deslocamento. {@code BATCHPGT.NSP:189} usa
 * {@code READ LOGICAL BY CPF}, e recomecar de um CPF conhecido mantem a leitura estavel
 * mesmo com inclusoes concorrentes — o que {@code OFFSET} nao garante.
 */
public interface BeneficiaryPayrollFeed {

    /**
     * @param afterCpf CPF exclusivo de inicio; nulo na primeira pagina
     * @param limit tamanho maximo da pagina
     */
    List<PayrollCandidate> nextPage(String programCode, String afterCpf, int limit);
}
