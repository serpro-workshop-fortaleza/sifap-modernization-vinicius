package br.gov.sifap.beneficiary;

/**
 * Dados de mudanca de situacao cadastral.
 *
 * <p>Atende {@code REQ-BEN-004} e {@code REQ-BEN-006}. O motivo e obrigatorio: e ele que
 * permite, no futuro, derivar dos dados quais transicoes ocorrem de fato. O
 * [ADR-0006] registra que nenhuma maquina de estados foi definida, por falta de fonte.
 */
public record ChangeBeneficiaryStatusCommand(BeneficiaryStatus newStatus, String reason) {
}
