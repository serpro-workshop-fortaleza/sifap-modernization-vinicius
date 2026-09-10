package br.gov.sifap.beneficiary.internal.migration;

import br.gov.sifap.beneficiary.internal.migration.LegacyRecord.LegacyBeneficiary;
import br.gov.sifap.beneficiary.internal.migration.LegacyRecord.LegacyDependent;
import java.util.Optional;

/**
 * Recorte posicional do arquivo de extracao.
 *
 * <p>Linha mais curta que o layout e tolerada: os campos ausentes vem vazios e viram
 * pendencia, em vez de interromper a carga.
 */
final class LegacyRecordParser {

    private LegacyRecordParser() {
    }

    static Optional<LegacyRecord> parse(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        return switch (line.charAt(0)) {
            case '1' -> Optional.of(beneficiary(line));
            case '2' -> Optional.of(dependent(line));
            default -> Optional.empty();
        };
    }

    private static LegacyBeneficiary beneficiary(String line) {
        return new LegacyBeneficiary(
                slice(line, 1, 12),
                slice(line, 12, 72),
                slice(line, 72, 80),
                slice(line, 80, 81),
                slice(line, 81, 82),
                slice(line, 82, 86),
                slice(line, 86, 97),
                slice(line, 97, 108),
                slice(line, 108, 168),
                slice(line, 168, 208),
                slice(line, 208, 210),
                slice(line, 210, 218),
                slice(line, 218, 220),
                slice(line, 220, 228));
    }

    private static LegacyDependent dependent(String line) {
        return new LegacyDependent(
                slice(line, 1, 12),
                slice(line, 12, 23),
                slice(line, 23, 83),
                slice(line, 83, 91),
                slice(line, 91, 93),
                slice(line, 93, 94),
                slice(line, 94, 95));
    }

    private static String slice(String line, int from, int to) {
        if (from >= line.length()) {
            return "";
        }
        return line.substring(from, Math.min(to, line.length())).trim();
    }
}
