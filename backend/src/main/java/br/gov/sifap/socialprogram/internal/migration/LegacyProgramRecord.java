package br.gov.sifap.socialprogram.internal.migration;

import java.util.Optional;

/**
 * Uma linha do arquivo de extracao de {@code SOCPROG}, ainda como texto.
 *
 * <p>{@code hasBands} e {@code hasRegions} sao indicadores de presenca, e nao os dados: o
 * {@code REQ-PRG-015} exige saber <em>quantos</em> programas tem parametrizacao preenchida
 * antes de decidir o que fazer com ela.
 */
record LegacyProgramRecord(
        String code,
        String name,
        String type,
        String status,
        String amountBase,
        String adjustmentFactor,
        String specialFactor,
        String maxPerCapitaIncome,
        String ageMin,
        String ageMax,
        String eligibilityCode,
        String startedAt,
        boolean hasBands,
        boolean hasRegions) {

    static Optional<LegacyProgramRecord> parse(String line) {
        if (line == null || line.isBlank() || line.charAt(0) != '1') {
            return Optional.empty();
        }
        return Optional.of(new LegacyProgramRecord(
                slice(line, 1, 5),
                slice(line, 5, 65),
                slice(line, 65, 66),
                slice(line, 66, 67),
                slice(line, 67, 76),
                slice(line, 76, 83),
                slice(line, 83, 92),
                slice(line, 92, 101),
                slice(line, 101, 104),
                slice(line, 104, 107),
                slice(line, 107, 112),
                slice(line, 112, 120),
                "S".equalsIgnoreCase(slice(line, 120, 121)),
                "S".equalsIgnoreCase(slice(line, 121, 122))));
    }

    private static String slice(String line, int from, int to) {
        if (from >= line.length()) {
            return "";
        }
        return line.substring(from, Math.min(to, line.length())).trim();
    }
}
