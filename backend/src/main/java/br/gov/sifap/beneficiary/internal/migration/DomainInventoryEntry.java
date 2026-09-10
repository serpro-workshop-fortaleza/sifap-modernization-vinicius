package br.gov.sifap.beneficiary.internal.migration;

/**
 * Uma linha do inventario de valores fora do dominio declarado.
 *
 * <p>Atende {@code REQ-BEN-021}. Sem esta medicao, escolher o dominio de grau de
 * parentesco seria adivinhar: o dicionario declara {@code FI}, {@code CJ}, {@code NT} e
 * {@code TU}, o programa aceita {@code FI}, {@code CO}, {@code IR} e {@code OU}, e apenas
 * {@code FI} coincide.
 */
public record DomainInventoryEntry(String fieldName, String value, long occurrences) {
}
