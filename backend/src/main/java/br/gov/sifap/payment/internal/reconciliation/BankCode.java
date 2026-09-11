package br.gov.sifap.payment.internal.reconciliation;

import java.util.Set;

/**
 * Codigo FEBRABAN do banco pagador.
 *
 * <p>Atende {@code REQ-REC-016}. {@code PAYMENT.ddm:72} declara
 * {@code EA COD-BANK A 3 N D FEBRABAN CODE} e {@code BATCHCON.NSP:212} grava
 * {@code MOVE 1 TO PAYMENT-V.COD-BANK}: um literal numerico em campo alfanumerico. O banco
 * que efetivamente creditou nunca foi preservado.
 */
record BankCode(String value) {

    /** Bancos que operam a folha. O Banco Real saiu em 2007, com a compra pelo Santander. */
    private static final Set<String> KNOWN = Set.of("001", "104", "033", "237", "341");

    static BankCode of(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!KNOWN.contains(normalized)) {
            throw new IllegalArgumentException(
                    "codigo de banco fora do dominio conhecido: " + normalized);
        }
        return new BankCode(normalized);
    }
}
