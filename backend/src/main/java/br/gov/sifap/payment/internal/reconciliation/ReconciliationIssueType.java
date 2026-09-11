package br.gov.sifap.payment.internal.reconciliation;

/**
 * Motivo pelo qual um registro de retorno nao foi conciliado.
 *
 * <p>Cada valor corresponde a um caminho que o {@code BATCHCON} resolve escrevendo no log
 * e seguindo adiante.
 */
enum ReconciliationIssueType {

    /** {@code BATCHCON.NSP:181-187}: nenhum pagamento com o numero informado. */
    SEM_CORRESPONDENCIA,
    /** {@code BATCHCON.NSP:237-242}: ramo {@code NONE}, contado como conciliado. */
    CODIGO_DESCONHECIDO,
    /** Par CPF e periodo com mais de um pagamento; so ocorre no historico migrado. */
    AMBIGUIDADE,
    /** Valor ou data ilegiveis no registro de retorno. */
    VALOR_INVALIDO,
    /** {@code BATCHCON.NSP:212} grava o literal `1` em campo de codigo FEBRABAN. */
    BANCO_DESCONHECIDO,
    /** Retorno para pagamento em situacao que nao o admite. */
    TRANSICAO_INVALIDA
}
