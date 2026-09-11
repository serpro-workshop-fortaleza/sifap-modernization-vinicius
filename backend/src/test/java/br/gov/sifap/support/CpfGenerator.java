package br.gov.sifap.support;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gera CPFs validos e distintos para testes de integracao.
 *
 * <p>O banco de teste e compartilhado entre classes; reutilizar um CPF fixo faz o segundo
 * teste falhar por um motivo que nada tem a ver com o que ele verifica.
 */
public final class CpfGenerator {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(100_000_000);

    private CpfGenerator() {
    }

    public static String next() {
        String base = String.valueOf(SEQUENCE.incrementAndGet());
        int first = checkDigit(base, 10);
        int second = checkDigit(base + first, 11);
        return base + first + second;
    }

    private static int checkDigit(String digits, int startWeight) {
        int sum = 0;
        int weight = startWeight;
        for (int index = 0; index < digits.length(); index++) {
            sum += Character.getNumericValue(digits.charAt(index)) * weight--;
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
