package br.gov.sifap.socialprogram;

import java.math.BigDecimal;
import java.util.List;

/**
 * Conjunto completo de faixas de calculo de um programa.
 *
 * <p>Substituicao em bloco, e nao item a item: a coerencia e do conjunto, e validar
 * sobreposicao incrementalmente permitiria estados intermediarios invalidos.
 */
public record ReplaceCalculationBandsCommand(List<BandData> bands) {

    public ReplaceCalculationBandsCommand {
        bands = List.copyOf(bands);
    }

    /** @param incomeTo nulo na faixa aberta no topo */
    public record BandData(
            BigDecimal incomeFrom,
            BigDecimal incomeTo,
            BigDecimal multiplier,
            BigDecimal additionalAmount,
            boolean accumulates) {
    }
}
