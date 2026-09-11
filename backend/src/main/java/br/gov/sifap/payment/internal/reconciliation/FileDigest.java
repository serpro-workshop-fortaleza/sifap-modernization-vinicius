package br.gov.sifap.payment.internal.reconciliation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Resumo do conteudo do arquivo de retorno.
 *
 * <p>Atende {@code REQ-REC-003}. O algoritmo nao foi escolhido aqui:
 * {@code PAYMENT.ddm:110} declara {@code SHA-256 RETURN FILE} desde 17/11/2015.
 */
final class FileDigest {

    private FileDigest() {
    }

    static String of(List<String> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String line : lines) {
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 indisponivel na plataforma", unavailable);
        }
    }
}
