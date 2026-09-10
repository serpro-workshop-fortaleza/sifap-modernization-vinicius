package br.gov.sifap.beneficiary.internal;

import br.gov.sifap.beneficiary.AddressData;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Set;

/**
 * Endereco do beneficiario.
 *
 * <p>Atende {@code REQ-BEN-018}. O logradouro comporta 120 caracteres, contra os 60 do
 * arquivo legado, onde {@code CADBENEF.NSP:277-279} grava um campo de 80 e perde os
 * ultimos 20 desde o ticket 4471/2003.
 */
@Embeddable
public class Address {

    /** Tabela de {@code VALBENEF.NSN:73-100}, codificada no fonte legado. */
    private static final Set<String> VALID_UF = Set.of(
            "AC", "AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA", "MG", "MS", "MT",
            "PA", "PB", "PE", "PI", "PR", "RJ", "RN", "RO", "RR", "RS", "SC", "SE", "SP", "TO");

    @Column(name = "street", length = 120)
    private String street;

    @Column(name = "street_number", length = 10)
    private String number;

    @Column(name = "complement", length = 30)
    private String complement;

    @Column(name = "district", length = 40)
    private String district;

    @Column(name = "city", length = 40)
    private String city;

    @Column(name = "uf", length = 2)
    private String uf;

    @Column(name = "postal_code", length = 8)
    private String postalCode;

    @Column(name = "region_code", length = 2)
    private String regionCode;

    protected Address() {
        // exigido pelo JPA
    }

    private Address(AddressData data) {
        this.street = trimToNull(data.street());
        this.number = trimToNull(data.number());
        this.complement = trimToNull(data.complement());
        this.district = trimToNull(data.district());
        this.city = trimToNull(data.city());
        this.uf = trimToNull(data.uf());
        this.postalCode = trimToNull(data.postalCode());
        this.regionCode = trimToNull(data.regionCode());
    }

    static Address of(AddressData data) {
        return data == null ? new Address() : new Address(data);
    }

    public static boolean isValidUf(String uf) {
        // VALBENEF.NSN:156 aceita unidade federativa em branco sem verificar.
        return uf == null || uf.isBlank() || VALID_UF.contains(uf.trim().toUpperCase());
    }

    public AddressData toData() {
        return new AddressData(street, number, complement, district, city, uf, postalCode, regionCode);
    }

    public String uf() {
        return uf;
    }

    public String regionCode() {
        return regionCode;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
