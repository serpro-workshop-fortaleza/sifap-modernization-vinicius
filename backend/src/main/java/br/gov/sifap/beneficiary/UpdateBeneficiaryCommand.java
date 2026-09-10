package br.gov.sifap.beneficiary;

import java.math.BigDecimal;

/**
 * Dados de alteracao cadastral.
 *
 * <p>Atende {@code REQ-BEN-005}. <strong>Nao existe campo de situacao.</strong> A garantia
 * vem da forma do tipo, e nao de validacao: nao ha caminho para o valor chegar.
 *
 * <p>No legado, {@code CADBENEF.NSP:314} grava o conteudo residual de {@code #STATUS}
 * sobre a situacao vigente, e {@code VALELEG.NSN:133-151} trata o branco resultante como
 * elegivel. Alterar o endereco de um beneficiario suspenso o reativa.
 */
public record UpdateBeneficiaryCommand(
        String fullName,
        String programCode,
        BigDecimal familyIncome,
        AddressData address,
        String phoneMobile,
        String rgNumber) {
}
