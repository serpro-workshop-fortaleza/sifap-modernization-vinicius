package br.gov.sifap.payment;

import java.util.List;

/**
 * Resultado da apuracao de elegibilidade.
 *
 * <p>Atende {@code REQ-PAY-009}. {@code VALELEG.NSN:236} acumula ate dez motivos e devolve
 * apenas {@code #REASON(1)}: quem corrige um impedimento descobre o seguinte na tentativa
 * seguinte, um de cada vez.
 *
 * @param waivedByRegion verdadeiro quando a elegibilidade veio da dispensa por regiao
 *     especial, preservada em nivel {@code PS} ({@code REQ-PAY-008})
 */
public record EligibilityResult(boolean eligible, List<String> reasons, boolean waivedByRegion) {

    public EligibilityResult {
        reasons = List.copyOf(reasons);
    }

    public static EligibilityResult approved() {
        return new EligibilityResult(true, List.of(), false);
    }

    /** Elegivel por dispensa regional, sem verificacao das demais condicoes. */
    public static EligibilityResult waived() {
        return new EligibilityResult(true, List.of(), true);
    }

    public static EligibilityResult ineligible(List<String> reasons) {
        return new EligibilityResult(false, reasons, false);
    }
}
