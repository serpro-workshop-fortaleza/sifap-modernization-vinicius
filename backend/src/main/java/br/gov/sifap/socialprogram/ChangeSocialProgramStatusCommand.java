package br.gov.sifap.socialprogram;

import java.time.LocalDate;

/**
 * Dados de mudanca de situacao.
 *
 * @param closedAt exigido quando a nova situacao e {@code ENCERRADO} ({@code REQ-PRG-007})
 */
public record ChangeSocialProgramStatusCommand(
        SocialProgramStatus newStatus, String reason, LocalDate closedAt) {
}
