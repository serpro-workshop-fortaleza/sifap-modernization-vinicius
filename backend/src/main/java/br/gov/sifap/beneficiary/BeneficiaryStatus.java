package br.gov.sifap.beneficiary;

import java.util.Optional;

/**
 * Situacao cadastral do beneficiario.
 *
 * <p>Atende {@code REQ-BEN-004}. Dominio declarado em {@code BENEFIC.ddm:74}.
 *
 * <p>Nao existe valor de reserva nem estado de migracao. Sinalizacao de carga vive em
 * tabela propria, para que a Fatia 4 nao precise conhecer um estado que so existe por
 * causa da migracao.
 *
 * <p>E o unico tipo de dominio deste contexto que atravessa a fronteira: a apuracao de
 * elegibilidade precisa dele.
 */
public enum BeneficiaryStatus {

    ATIVO('A'),
    SUSPENSO('S'),
    CANCELADO('C'),
    INATIVO('I'),
    DESLIGADO('D');

    private final char legacyCode;

    BeneficiaryStatus(char legacyCode) {
        this.legacyCode = legacyCode;
    }

    public char legacyCode() {
        return legacyCode;
    }

    /**
     * Converte o codigo de uma letra do arquivo legado.
     *
     * <p>Devolve vazio para qualquer valor fora do dominio, inclusive branco — que e o
     * que {@code CADBENEF.NSP:314} grava em toda alteracao de beneficiario com ate 75
     * anos. A carga sinaliza esses registros em vez de atribuir situacao.
     */
    public static Optional<BeneficiaryStatus> fromLegacyCode(String code) {
        if (code == null || code.isBlank() || code.trim().length() != 1) {
            return Optional.empty();
        }
        char value = code.trim().charAt(0);
        for (BeneficiaryStatus status : values()) {
            if (status.legacyCode == value) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
