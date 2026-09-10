package br.gov.sifap.beneficiary.internal.migration;

/**
 * Motivos de pendencia de migracao.
 *
 * <p>Cada valor corresponde a uma regra do sistema novo que o dado de origem nao satisfaz.
 * O registro entra com a pendencia; nenhum e recusado ({@code REQ-BEN-020}).
 */
public enum MigrationIssueType {

    CPF_INVALIDO,
    NIS_INVALIDO,
    NIS_DUPLICADO,
    NOME_SEM_SOBRENOME,
    DATA_NASCIMENTO_ILEGIVEL,
    SITUACAO_AUSENTE,
    SEXO_FORA_DO_DOMINIO,
    UF_FORA_DO_DOMINIO,
    PARENTESCO_FORA_DO_DOMINIO,
    SITUACAO_DEPENDENTE_AUSENTE,
    CPF_DEPENDENTE_INVALIDO,
    TITULAR_INEXISTENTE,
    LIMITE_DE_DEPENDENTES_EXCEDIDO
}
