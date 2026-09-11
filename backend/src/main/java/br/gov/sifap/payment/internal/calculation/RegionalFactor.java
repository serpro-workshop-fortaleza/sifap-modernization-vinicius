package br.gov.sifap.payment.internal.calculation;

import br.gov.sifap.shared.exception.DomainRuleException;
import br.gov.sifap.socialprogram.SocialProgramView.RegionalParameterView;
import java.math.BigDecimal;
import java.util.List;

/**
 * Multiplicador regional.
 *
 * <p>Atende {@code REQ-PAY-011}. No legado, {@code CALCBENF.NSN:99-125} carrega 27 fatores
 * rotulados por unidade federativa e os indexa por {@code COD-REGION}, que
 * {@code BENEFIC.ddm:67} declara como {@code 01}-{@code 05} ou {@code 99}.
 *
 * <p>Apenas os indices 1 a 5 sao alcancaveis, e eles contem Acre, Amazonas, Amapa, Para e
 * Rondonia: todas as macrorregioes recebem multiplicadores do Norte. A carga preserva
 * esses valores; corrigir a correspondencia e decisao de negocio, registrada no ADR-0008.
 *
 * <p>O que muda e a origem: o fator vem da parametrizacao do programa, onde pode ser
 * alterado por quem tem autoridade.
 */
public final class RegionalFactor {

    private RegionalFactor() {
    }

    public static BigDecimal of(String regionCode, List<RegionalParameterView> parameters) {
        return parameters.stream()
                .filter(RegionalParameterView::active)
                .filter(parameter -> parameter.regionCode().equals(regionCode))
                .map(RegionalParameterView::multiplier)
                .findFirst()
                // AC-011.2: sem valor padrao silencioso. O legado adota 1.0000 para
                // qualquer regiao fora de 1..25, inclusive a especial.
                .orElseThrow(() -> new DomainRuleException(
                        "REQ-PAY-011",
                        "programa sem parametro regional vigente para a regiao " + regionCode));
    }
}
