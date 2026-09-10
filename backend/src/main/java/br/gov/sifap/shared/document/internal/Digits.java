package br.gov.sifap.shared.document.internal;

/**
 * Predicados sintaticos compartilhados pelos validadores de documento.
 *
 * <p>Existe para que CPF e NIS nao repitam a mesma verificacao, que foi a origem
 * das cinco implementacoes divergentes do legado.
 */
final class Digits {

    private Digits() {
    }

    /**
     * Verifica se todos os caracteres sao digitos ASCII.
     *
     * <p>Nao usa {@code Character.isDigit}, que aceita digitos Unicode de outros
     * alfabetos e permitiria entrada fora do dominio de um campo {@code A11} legado.
     */
    static boolean isAllAsciiDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    static boolean isAllZeros(String value) {
        return isRepetitionOf(value, '0');
    }

    static boolean isAllSameDigit(String value) {
        return isRepetitionOf(value, value.charAt(0));
    }

    private static boolean isRepetitionOf(String value, char digit) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) != digit) {
                return false;
            }
        }
        return true;
    }
}
