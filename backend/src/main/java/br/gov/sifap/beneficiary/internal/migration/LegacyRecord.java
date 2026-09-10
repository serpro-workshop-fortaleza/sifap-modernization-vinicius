package br.gov.sifap.beneficiary.internal.migration;

/**
 * Uma linha do arquivo de extracao, ainda como texto.
 *
 * <p>O layout e posicional porque a origem e uma descarga do Adabas, e nao uma consulta:
 * nao ha conectividade com o mainframe, e o {@code FDT-150-BENEFICIARY.txt} estima 90
 * minutos para a descarga completa.
 *
 * <p>Nenhum campo e convertido aqui. Conversao que falha vira pendencia, e por isso o
 * texto original precisa sobreviver ate o momento de registra-la.
 */
sealed interface LegacyRecord {

    /** Registro de beneficiario, tipo {@code 1}. */
    record LegacyBeneficiary(
            String cpf,
            String fullName,
            String birthDate,
            String sex,
            String status,
            String programCode,
            String nis,
            String familyIncome,
            String street,
            String city,
            String uf,
            String postalCode,
            String regionCode,
            String registeredAt)
            implements LegacyRecord {
    }

    /** Registro de dependente, tipo {@code 2}. */
    record LegacyDependent(
            String holderCpf,
            String cpf,
            String fullName,
            String birthDate,
            String relation,
            String status,
            String disability)
            implements LegacyRecord {
    }
}
