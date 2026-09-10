package br.gov.sifap.beneficiary;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Projecao de leitura de um dependente.
 *
 * @param maskedCpf vazio quando o dependente nao possui CPF ({@code REQ-BEN-011})
 */
public record DependentView(
        Optional<String> maskedCpf,
        String fullName,
        Optional<LocalDate> birthDate,
        Relationship relation,
        DependentStatus status,
        boolean disability) {
}
