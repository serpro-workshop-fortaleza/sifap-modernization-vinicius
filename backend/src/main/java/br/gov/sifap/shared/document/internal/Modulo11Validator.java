package br.gov.sifap.shared.document.internal;

/**
 * Calculo de digito verificador por modulo 11, compartilhado por CPF e NIS.
 *
 * <p>Apoia {@code REQ-DOC-003} e {@code REQ-DOC-005}.
 * Origem legada: {@code CCVALCPF.NSC:92-128} e {@code SUBVALNI.NSN:102-150}.
 */
public final class Modulo11Validator {

    private Modulo11Validator() {
    }

    /**
     * Calcula o digito verificador aplicando os pesos aos primeiros digitos informados.
     *
     * @param digits sequencia de digitos ASCII ja validada pelo chamador
     * @param weights pesos posicionais; define quantos digitos participam da soma
     * @return o digito verificador esperado, entre 0 e 9
     */
    public static int checkDigit(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        int remainder = sum % 11;
        // As duas rotinas legadas convergem nesta regra: resto 0 ou 1 produz digito zero.
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
