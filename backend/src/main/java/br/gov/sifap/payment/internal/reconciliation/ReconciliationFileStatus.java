package br.gov.sifap.payment.internal.reconciliation;

/** Situacao do processamento de um arquivo de retorno. */
enum ReconciliationFileStatus {

    EM_ANDAMENTO,
    CONCLUIDO,
    /** Permite a retomada do {@code REQ-REC-015}. */
    INTERROMPIDO
}
