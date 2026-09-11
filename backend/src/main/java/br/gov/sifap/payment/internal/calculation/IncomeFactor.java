package br.gov.sifap.payment.internal.calculation;

import br.gov.sifap.shared.exception.DomainRuleException;
import br.gov.sifap.socialprogram.SocialProgramView.CalculationBandView;
import java.math.BigDecimal;
import java.util.List;

/**
 * Multiplicador da faixa de renda.
 *
 * <p>Atende {@code REQ-PAY-012}. E o defeito mais grave encontrado no projeto.
 *
 * <p>No {@code BATCHPGT}, {@code DET-INCOME-BAND-BATCH} percorre cinco faixas cuja ultima
 * termina em {@code 9.999,99}. Acima disso nenhuma casa, o laco termina sem atribuir
 * {@code #FACTOR-INCOME} e a variavel <strong>mantem o valor do beneficiario processado
 * imediatamente antes</strong>, porque nao e reinicializada a cada iteracao.
 *
 * <p>Dois beneficiarios com exatamente os mesmos dados recebem valores diferentes conforme
 * a ordem de leitura do arquivo. Esta implementacao e uma funcao total: ou ha faixa, ou
 * falha com motivo.
 */
public final class IncomeFactor {

    private IncomeFactor() {
    }

    public static BigDecimal of(BigDecimal declaredIncome, List<CalculationBandView> bands) {
        return bands.stream()
                .filter(band -> covers(band, declaredIncome))
                .map(CalculationBandView::multiplier)
                .findFirst()
                .orElseThrow(() -> new DomainRuleException(
                        "REQ-PAY-012",
                        "programa sem faixa de calculo que cubra a renda declarada"));
    }

    private static boolean covers(CalculationBandView band, BigDecimal income) {
        boolean aboveStart = income.compareTo(band.incomeFrom()) >= 0;
        boolean belowEnd = band.incomeTo().map(end -> income.compareTo(end) < 0).orElse(true);
        return aboveStart && belowEnd;
    }
}
