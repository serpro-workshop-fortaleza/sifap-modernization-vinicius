package br.gov.sifap.socialprogram.internal.migration;

import java.util.Map;

/**
 * Resultado da carga do catalogo.
 *
 * <p>Atende {@code REQ-PRG-015}. Nenhuma fonte do acervo permite saber o que ha nos 45
 * registros: o inventario e o primeiro momento em que isso fica conhecido.
 *
 * @param withAdjustedAmount programas cujo valor base foi gravado ja multiplicado pelo
 *     fator ({@code CADPROG.NSP:125-130}), e que portanto exibem um numero que ninguem
 *     digitou
 */
public record CatalogMigrationReport(
        int programsRead,
        int programsMigrated,
        int withCalculationBands,
        int withRegionalParameters,
        int withSpecialFactor,
        int withAdjustedAmount,
        Map<String, Long> valuesOutOfDomain) {

    public CatalogMigrationReport {
        valuesOutOfDomain = Map.copyOf(valuesOutOfDomain);
    }

    public boolean noProgramDiscarded() {
        return programsRead == programsMigrated;
    }
}
