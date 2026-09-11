package br.gov.sifap.payment;

/**
 * Fatores que compoem o calculo do beneficio.
 *
 * <p>Existe para que cada fator aplicado seja recuperavel ({@code AC-010.2}). Nao e
 * conveniencia de depuracao: e o que torna a divergencia entre a {@code RN-013} e a
 * formula que roda uma grandeza medivel depois de uma folha real.
 */
public enum FactorType {
    REGIONAL,
    FAMILIAR,
    RENDA,
    ETARIO,
    AJUSTE_DO_PROGRAMA
}
