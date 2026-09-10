package br.gov.sifap.beneficiary;

/**
 * Endereco informado.
 *
 * <p>Segue a estrutura do dicionario ({@code BENEFIC.ddm:57-67}), que separa logradouro,
 * numero e complemento. O {@code CADBENEF} nao oferece esses campos na tela e grava 80
 * caracteres em um campo de 60, perdendo os ultimos 20 ({@code REQ-BEN-018}).
 */
public record AddressData(
        String street,
        String number,
        String complement,
        String district,
        String city,
        String uf,
        String postalCode,
        String regionCode) {
}
