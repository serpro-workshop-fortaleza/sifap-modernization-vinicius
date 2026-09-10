package br.gov.sifap.beneficiary.internal.migration;

import br.gov.sifap.beneficiary.AddressData;
import br.gov.sifap.beneficiary.BeneficiaryStatus;
import br.gov.sifap.beneficiary.DependentStatus;
import br.gov.sifap.beneficiary.Relationship;
import br.gov.sifap.beneficiary.Sex;
import br.gov.sifap.beneficiary.internal.Address;
import br.gov.sifap.beneficiary.internal.Beneficiary;
import br.gov.sifap.beneficiary.internal.BeneficiaryRepository;
import br.gov.sifap.beneficiary.internal.MigratedBeneficiary;
import br.gov.sifap.beneficiary.internal.migration.LegacyRecord.LegacyBeneficiary;
import br.gov.sifap.beneficiary.internal.migration.LegacyRecord.LegacyDependent;
import br.gov.sifap.shared.document.Cpf;
import br.gov.sifap.shared.document.Nis;
import br.gov.sifap.shared.event.Actor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga inicial do cadastro.
 *
 * <p>Atende {@code REQ-BEN-020} e {@code REQ-BEN-021}.
 *
 * <p>A regra que organiza este componente e curta: <strong>nenhum beneficiario e
 * descartado</strong>. Todo dado que nao satisfaz as regras novas entra assim mesmo, com
 * uma pendencia registrada. Decidir o destino de um beneficiario cujo CPF se tornou
 * invalido pela unificacao do ADR-0005, ou cuja marca de suspensao foi apagada por
 * {@code CADBENEF.NSP:314}, e competencia de negocio.
 */
@Component
public class BeneficiaryLoader {

    private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_ACTIVE_DEPENDENTS = 6;

    private final BeneficiaryRepository beneficiaries;
    private final MigrationIssueRepository issues;
    private final Clock clock;

    public BeneficiaryLoader(
            BeneficiaryRepository beneficiaries, MigrationIssueRepository issues, Clock clock) {
        this.beneficiaries = beneficiaries;
        this.issues = issues;
        this.clock = clock;
    }

    @Transactional
    public MigrationReport load(List<String> lines, Actor actor) {
        Instant detectedAt = clock.instant();
        Map<MigrationIssueType, Long> counters = new EnumMap<>(MigrationIssueType.class);
        List<MigrationIssue> pending = new ArrayList<>();
        // O lote ainda nao foi confirmado, entao a restricao do banco nao enxerga
        // duplicidade dentro da propria carga.
        Set<String> nisSeen = new HashSet<>();

        int beneficiariesRead = 0;
        int beneficiariesMigrated = 0;
        int dependentsRead = 0;
        int dependentsMigrated = 0;

        for (String line : lines) {
            Optional<LegacyRecord> parsed = LegacyRecordParser.parse(line);
            if (parsed.isEmpty()) {
                continue;
            }

            if (parsed.get() instanceof LegacyBeneficiary record) {
                beneficiariesRead++;
                beneficiariesMigrated +=
                        migrate(record, actor, detectedAt, pending, counters, nisSeen) ? 1 : 0;
            } else if (parsed.get() instanceof LegacyDependent record) {
                dependentsRead++;
                dependentsMigrated += attach(record, detectedAt, pending, counters) ? 1 : 0;
            }
        }

        issues.saveAll(pending);
        return new MigrationReport(
                beneficiariesRead, beneficiariesMigrated, dependentsRead, dependentsMigrated, counters);
    }

    private boolean migrate(
            LegacyBeneficiary record,
            Actor actor,
            Instant detectedAt,
            List<MigrationIssue> pending,
            Map<MigrationIssueType, Long> counters,
            Set<String> nisSeen) {

        String cpf = record.cpf();

        if (Cpf.tryParse(cpf).isEmpty()) {
            record(pending, counters, cpf, MigrationIssueType.CPF_INVALIDO, "cpf", cpf, detectedAt);
        }

        String nis = null;
        if (!record.nis().isBlank() && !isAllZeros(record.nis())) {
            Optional<Nis> parsedNis = Nis.tryParse(record.nis());
            if (parsedNis.isEmpty()) {
                record(pending, counters, cpf, MigrationIssueType.NIS_INVALIDO, "nis", record.nis(), detectedAt);
            } else if (!nisSeen.add(parsedNis.get().value())
                    || beneficiaries.existsByNis(parsedNis.get().value())) {
                // O dicionario declara AM NUM-NIS como unico; se a origem trouxer repeticao,
                // o registro entra sem o documento em vez de derrubar a carga.
                record(pending, counters, cpf, MigrationIssueType.NIS_DUPLICADO, "nis", record.nis(), detectedAt);
            } else {
                nis = parsedNis.get().value();
            }
        }

        String fullName = record.fullName();
        if (!fullName.contains(" ")) {
            record(pending, counters, cpf, MigrationIssueType.NOME_SEM_SOBRENOME, "fullName", fullName, detectedAt);
        }

        LocalDate birthDate = parseDate(record.birthDate()).orElse(null);
        if (birthDate == null) {
            record(pending, counters, cpf, MigrationIssueType.DATA_NASCIMENTO_ILEGIVEL,
                    "birthDate", record.birthDate(), detectedAt);
        }

        // Situacao ausente e o efeito de CADBENEF.NSP:314. Nenhuma atribuicao automatica:
        // decidir se um suspenso reativado em 2014 esta hoje ativo e decisao de negocio.
        BeneficiaryStatus status = BeneficiaryStatus.fromLegacyCode(record.status()).orElse(null);
        if (status == null) {
            record(pending, counters, cpf, MigrationIssueType.SITUACAO_AUSENTE,
                    "status", record.status(), detectedAt);
        }

        Sex sex = Sex.fromLegacyCode(record.sex()).orElse(null);
        if (sex == null) {
            record(pending, counters, cpf, MigrationIssueType.SEXO_FORA_DO_DOMINIO,
                    "sex", record.sex(), detectedAt);
            sex = Sex.I;
        }

        if (!record.uf().isBlank() && !isKnownUf(record.uf())) {
            record(pending, counters, cpf, MigrationIssueType.UF_FORA_DO_DOMINIO,
                    "uf", record.uf(), detectedAt);
        }

        AddressData address = new AddressData(
                emptyToNull(record.street()),
                null,
                null,
                null,
                emptyToNull(record.city()),
                emptyToNull(record.uf()),
                emptyToNull(record.postalCode()),
                emptyToNull(record.regionCode()));

        MigratedBeneficiary data = new MigratedBeneficiary(
                cpf,
                nis,
                fullName.isBlank() ? "(nome ausente na origem)" : fullName,
                birthDate,
                sex,
                status,
                emptyToNull(record.programCode()),
                parseAmount(record.familyIncome()),
                address,
                parseDate(record.registeredAt()).orElse(LocalDate.now(clock)));

        beneficiaries.save(Beneficiary.migrated(data, actor, clock));
        return true;
    }

    private boolean attach(
            LegacyDependent record,
            Instant detectedAt,
            List<MigrationIssue> pending,
            Map<MigrationIssueType, Long> counters) {

        Optional<Beneficiary> holder = beneficiaries.findByCpf(record.holderCpf());
        if (holder.isEmpty()) {
            record(pending, counters, record.holderCpf(), MigrationIssueType.TITULAR_INEXISTENTE,
                    "holderCpf", record.holderCpf(), detectedAt);
            return false;
        }

        Beneficiary beneficiary = holder.get();

        Relationship relation = Relationship.fromLegacyCode(record.relation()).orElse(null);
        if (relation == null) {
            record(pending, counters, record.holderCpf(), MigrationIssueType.PARENTESCO_FORA_DO_DOMINIO,
                    "relation", record.relation(), detectedAt);
            relation = Relationship.OU;
        }

        DependentStatus status = DependentStatus.fromLegacyCode(record.status()).orElse(null);
        if (status == null) {
            record(pending, counters, record.holderCpf(), MigrationIssueType.SITUACAO_DEPENDENTE_AUSENTE,
                    "dependentStatus", record.status(), detectedAt);
            // CADDEPEN.NSP:194-202 nunca grava a situacao. Ativo e o unico estado
            // compativel com um dependente que o legado conta como existente.
            status = DependentStatus.ATIVO;
        }

        String cpf = null;
        if (!record.cpf().isBlank() && !isAllZeros(record.cpf())) {
            Optional<Cpf> parsed = Cpf.tryParse(record.cpf());
            if (parsed.isEmpty()) {
                record(pending, counters, record.holderCpf(), MigrationIssueType.CPF_DEPENDENTE_INVALIDO,
                        "dependentCpf", record.cpf(), detectedAt);
            } else {
                cpf = parsed.get().value();
            }
        }

        if (beneficiary.activeDependentCount() >= MAX_ACTIVE_DEPENDENTS) {
            record(pending, counters, record.holderCpf(), MigrationIssueType.LIMITE_DE_DEPENDENTES_EXCEDIDO,
                    "activeDependentCount", String.valueOf(beneficiary.activeDependentCount() + 1), detectedAt);
        }

        beneficiary.addMigratedDependent(
                cpf,
                record.fullName().isBlank() ? "(nome ausente na origem)" : record.fullName(),
                parseDate(record.birthDate()).orElse(null),
                relation,
                status,
                "S".equalsIgnoreCase(record.disability()));
        return true;
    }

    private static void record(
            List<MigrationIssue> pending,
            Map<MigrationIssueType, Long> counters,
            String sourceCpf,
            MigrationIssueType type,
            String field,
            String originalValue,
            Instant detectedAt) {
        pending.add(new MigrationIssue(sourceCpf, type, field, originalValue, detectedAt));
        counters.merge(type, 1L, Long::sum);
    }

    private static Optional<LocalDate> parseDate(String value) {
        if (value == null || value.isBlank() || value.length() != 8 || isAllZeros(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value, LEGACY_DATE));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Valor legado vem sem separador decimal, com duas casas implicitas. */
    private static BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank() || !value.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return new BigDecimal(value).movePointLeft(2);
    }

    private static boolean isAllZeros(String value) {
        return !value.isEmpty() && value.chars().allMatch(c -> c == '0');
    }

    private static boolean isKnownUf(String uf) {
        return Address.isValidUf(uf);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
