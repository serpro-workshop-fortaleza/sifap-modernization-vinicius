package br.gov.sifap.beneficiary;

import java.time.LocalDate;

/**
 * Dados de inclusao de dependente.
 *
 * @param cpf pode ser nulo; o legado representa a ausencia por onze zeros
 *     ({@code BENEFIC.ddm:88}) e o {@code REQ-BEN-011} a representa como ausencia
 */
public record AddDependentCommand(
        String cpf,
        String fullName,
        LocalDate birthDate,
        Relationship relation,
        boolean disability) {
}
