package br.gov.sifap.audit;

import java.time.Period;
import java.util.Objects;

/**
 * Prazos de retencao da trilha.
 *
 * <p>Cobre {@code REQ-AUD-013} e apoia {@code REQ-AUD-007}. O minimo legal de dez anos
 * para eventos de alteracao ({@code Lei 8159, art. 14}) e verificado na construcao:
 * uma configuracao invalida impede a aplicacao de subir, em vez de expurgar dado que
 * deveria ser preservado.
 *
 * <p>A retencao de acesso e livre por decisao do {@code plan.md}: registra o acesso,
 * atendendo a {@code IN-TCU 63/2010}, e limita o crescimento que motivou a
 * {@code PORT. CGTI 213/2010}.
 */
public record AuditRetentionPolicy(Period changeRetention, Period accessRetention) {

    private static final Period LEGAL_MINIMUM = Period.ofYears(10);

    public AuditRetentionPolicy {
        Objects.requireNonNull(changeRetention, "changeRetention");
        Objects.requireNonNull(accessRetention, "accessRetention");

        if (changeRetention.toTotalMonths() < LEGAL_MINIMUM.toTotalMonths()) {
            throw new IllegalArgumentException(
                    "retencao de eventos de alteracao nao pode ser inferior a dez anos");
        }
    }

    public static AuditRetentionPolicy legalMinimumWithAccess(Period accessRetention) {
        return new AuditRetentionPolicy(LEGAL_MINIMUM, accessRetention);
    }
}
