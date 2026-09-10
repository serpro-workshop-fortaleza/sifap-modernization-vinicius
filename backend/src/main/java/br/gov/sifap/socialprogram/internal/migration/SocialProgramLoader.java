package br.gov.sifap.socialprogram.internal.migration;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.socialprogram.RegisterSocialProgramCommand;
import br.gov.sifap.socialprogram.SocialProgramStatus;
import br.gov.sifap.socialprogram.SocialProgramType;
import br.gov.sifap.socialprogram.internal.SocialProgram;
import br.gov.sifap.socialprogram.internal.SocialProgramRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga inicial do catalogo, com inventario da parametrizacao.
 *
 * <p>Atende {@code REQ-PRG-015}. O inventario existe porque nenhuma fonte do acervo diz o
 * que ha nos 45 registros: as faixas de calculo e os parametros regionais estao declarados
 * no dicionario desde 1997 e 2002, e nenhum programa os le ou escreve.
 *
 * <p><strong>Nenhum valor base e recalculado.</strong> Reverter o ajuste de
 * {@code CADPROG.NSP:125} exigiria o coeficiente vigente na data de cada inclusao, que nao
 * e registrada — e alterar valor base altera beneficio pago.
 */
@Component
public class SocialProgramLoader {

    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SocialProgramRepository repository;
    private final Clock clock;

    public SocialProgramLoader(SocialProgramRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public CatalogMigrationReport load(List<String> lines, Actor actor) {
        Map<String, Long> outOfDomain = new LinkedHashMap<>();
        int read = 0;
        int migrated = 0;
        int withBands = 0;
        int withRegions = 0;
        int withSpecialFactor = 0;
        int withAdjustedAmount = 0;

        for (String line : lines) {
            Optional<LegacyProgramRecord> parsed = LegacyProgramRecord.parse(line);
            if (parsed.isEmpty()) {
                continue;
            }
            LegacyProgramRecord record = parsed.get();
            read++;

            SocialProgramType type = SocialProgramType.fromLegacyCode(record.type()).orElse(null);
            if (type == null) {
                count(outOfDomain, "type=" + record.type());
                type = SocialProgramType.ASSISTENCIA;
            }

            SocialProgramStatus status =
                    SocialProgramStatus.fromLegacyCode(record.status()).orElse(null);
            if (status == null) {
                count(outOfDomain, "status=" + record.status());
            }

            BigDecimal adjustmentFactor = amount(record.adjustmentFactor(), 4);
            if (adjustmentFactor.signum() != 0) {
                // Fator diferente de zero implica que CADPROG.NSP:125 multiplicou o valor
                // base antes de grava-lo: o numero armazenado nao e o informado.
                withAdjustedAmount++;
            }
            if (amount(record.specialFactor(), 4).signum() != 0) {
                withSpecialFactor++;
            }
            if (record.hasBands()) {
                withBands++;
            }
            if (record.hasRegions()) {
                withRegions++;
            }

            SocialProgram program = SocialProgram.register(
                    new RegisterSocialProgramCommand(
                            record.code(),
                            record.name().isBlank() ? "(nome ausente na origem)" : record.name(),
                            null,
                            type,
                            positiveOrOne(amount(record.amountBase(), 2)),
                            adjustmentFactor,
                            amount(record.maxPerCapitaIncome(), 2),
                            integer(record.ageMin()),
                            integer(record.ageMax()),
                            emptyToNull(record.eligibilityCode()),
                            null,
                            parseDate(record.startedAt()).orElse(LocalDate.now(clock))),
                    actor,
                    clock);

            repository.save(program);
            migrated++;
        }

        return new CatalogMigrationReport(
                read, migrated, withBands, withRegions, withSpecialFactor, withAdjustedAmount, outOfDomain);
    }

    private static void count(Map<String, Long> counters, String key) {
        counters.merge(key, 1L, Long::sum);
    }

    /** Valor legado vem sem separador decimal, com casas implicitas. */
    private static BigDecimal amount(String value, int scale) {
        if (value == null || value.isBlank() || !value.chars().allMatch(Character::isDigit)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value).movePointLeft(scale);
    }

    /** O agregado exige valor base positivo; origem zerada entra como um e fica no relatorio. */
    private static BigDecimal positiveOrOne(BigDecimal value) {
        return value.signum() > 0 ? value : BigDecimal.ONE;
    }

    private static Integer integer(String value) {
        if (value == null || value.isBlank() || !value.chars().allMatch(Character::isDigit)) {
            return 0;
        }
        return Integer.valueOf(value);
    }

    private static Optional<LocalDate> parseDate(String value) {
        if (value == null || value.length() != 8 || value.chars().allMatch(c -> c == '0')) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value, LEGACY_DATE));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
