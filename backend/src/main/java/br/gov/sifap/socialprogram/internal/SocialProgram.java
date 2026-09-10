package br.gov.sifap.socialprogram.internal;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.Change;
import br.gov.sifap.shared.exception.DomainRuleException;
import br.gov.sifap.socialprogram.RegisterSocialProgramCommand;
import br.gov.sifap.socialprogram.ReplaceCalculationBandsCommand;
import br.gov.sifap.socialprogram.ReplaceCalculationBandsCommand.BandData;
import br.gov.sifap.socialprogram.ReplaceRegionalParametersCommand;
import br.gov.sifap.socialprogram.ReplaceRegionalParametersCommand.RegionData;
import br.gov.sifap.socialprogram.SocialProgramStatus;
import br.gov.sifap.socialprogram.SocialProgramType;
import br.gov.sifap.socialprogram.UpdateSocialProgramCommand;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Raiz do agregado de programa social.
 *
 * <p>45 registros deste tipo parametrizam 4,2 milhoes de beneficiarios, cerca de 93 mil
 * pessoas por programa. E {@code CADPROG.NSP:96-106} grava tudo que a tela devolve, sem
 * uma unica verificacao — nem de tipo, nem de valor, nem de faixa etaria.
 */
@Entity
@Table(name = "social_program")
public class SocialProgram {

    private static final int MAX_BANDS = 5;
    private static final int MAX_REGIONS = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "socialProgramSeq")
    @SequenceGenerator(
            name = "socialProgramSeq",
            sequenceName = "social_program_id_seq",
            allocationSize = 50)
    private Long id;

    @Column(name = "code", nullable = false, updatable = false, length = 4)
    private String code;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "acronym", length = 10)
    private String acronym;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 12)
    private SocialProgramType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private SocialProgramStatus status;

    @Column(name = "status_reason", length = 60)
    private String statusReason;

    @Column(name = "amount_base", nullable = false, precision = 9, scale = 2)
    private BigDecimal amountBase;

    @Column(name = "adjustment_factor", nullable = false, precision = 7, scale = 4)
    private BigDecimal adjustmentFactor;

    @Column(name = "max_percap_income", precision = 9, scale = 2)
    private BigDecimal maxPerCapitaIncome;

    @Column(name = "age_min", nullable = false)
    private short ageMin;

    @Column(name = "age_max", nullable = false)
    private short ageMax;

    @Column(name = "eligibility_code", length = 5)
    private String eligibilityCode;

    @Column(name = "creation_law", length = 20)
    private String creationLaw;

    @Column(name = "started_at", nullable = false)
    private LocalDate startedAt;

    @Column(name = "closed_at")
    private LocalDate closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // Set, e nao List: o Hibernate nao busca duas colecoes bag no mesmo fetch join.
    // A ordem e aplicada na projecao, nao na persistencia.
    @OneToMany(mappedBy = "socialProgram", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CalculationBand> bands = new LinkedHashSet<>();

    @OneToMany(mappedBy = "socialProgram", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RegionalParameter> regions = new LinkedHashSet<>();

    protected SocialProgram() {
        // exigido pelo JPA
    }

    // ---------------------------------------------------------------- criacao

    public static SocialProgram register(
            RegisterSocialProgramCommand command, Actor actor, Clock clock) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(actor, "actor");

        SocialProgram program = new SocialProgram();
        program.code = requireCode(command.code());
        program.name = requireName(command.name());
        program.acronym = trimToNull(command.acronym());
        program.type = requireType(command.type());
        program.amountBase = requireAmount(command.amountBase());
        program.adjustmentFactor = orZero(command.adjustmentFactor());
        program.maxPerCapitaIncome = command.maxPerCapitaIncome();
        program.applyAgeRange(command.ageMin(), command.ageMax());
        program.eligibilityCode = trimToNull(command.eligibilityCode());
        program.creationLaw = trimToNull(command.creationLaw());
        program.startedAt =
                command.startedAt() == null ? LocalDate.now(clock) : command.startedAt();

        Instant now = clock.instant();
        program.createdAt = now;
        program.createdBy = actor.id();
        program.updatedAt = now;
        program.updatedBy = actor.id();

        // Situacao inicial preservada de CADPROG.NSP:134.
        program.status = SocialProgramStatus.ATIVO;
        return program;
    }

    // ------------------------------------------------------------- alteracao

    /**
     * Aplica a alteracao e devolve os campos modificados.
     *
     * <p>Atende {@code REQ-PRG-006}, operacao que o legado nao possui. Um catalogo de
     * parametrizacao sem alteracao nao comporta reajuste anual, que e a razao de ele
     * existir.
     */
    public Map<String, Change> update(UpdateSocialProgramCommand command, Actor actor, Clock clock) {
        Objects.requireNonNull(command, "command");
        requireNotClosed();

        String newName = requireName(command.name());
        BigDecimal newAmount = requireAmount(command.amountBase());

        Map<String, Change> changes = new LinkedHashMap<>();
        recordChange(changes, "name", this.name, newName);
        recordChange(changes, "acronym", this.acronym, trimToNull(command.acronym()));
        recordChange(changes, "amountBase", text(this.amountBase), text(newAmount));
        recordChange(changes, "adjustmentFactor",
                text(this.adjustmentFactor), text(orZero(command.adjustmentFactor())));
        recordChange(changes, "maxPerCapitaIncome",
                text(this.maxPerCapitaIncome), text(command.maxPerCapitaIncome()));
        recordChange(changes, "ageMin", String.valueOf(this.ageMin), String.valueOf(orZero(command.ageMin())));
        recordChange(changes, "ageMax", String.valueOf(this.ageMax), String.valueOf(orZero(command.ageMax())));
        recordChange(changes, "eligibilityCode",
                this.eligibilityCode, trimToNull(command.eligibilityCode()));

        this.name = newName;
        this.acronym = trimToNull(command.acronym());
        this.amountBase = newAmount;
        this.adjustmentFactor = orZero(command.adjustmentFactor());
        this.maxPerCapitaIncome = command.maxPerCapitaIncome();
        applyAgeRange(command.ageMin(), command.ageMax());
        this.eligibilityCode = trimToNull(command.eligibilityCode());
        this.updatedAt = clock.instant();
        this.updatedBy = actor.id();
        return changes;
    }

    /** Atende {@code REQ-PRG-007}. */
    public SocialProgramStatus changeStatus(
            SocialProgramStatus newStatus, String reason, LocalDate closedAt, Actor actor, Clock clock) {
        Objects.requireNonNull(newStatus, "newStatus");
        if (reason == null || reason.isBlank()) {
            throw new DomainRuleException("REQ-PRG-007", "motivo da mudanca de situacao e obrigatorio");
        }
        if (!this.status.canTransitionTo(newStatus)) {
            throw new DomainRuleException(
                    "REQ-PRG-007",
                    "transicao de " + this.status + " para " + newStatus + " nao e permitida");
        }
        if (newStatus == SocialProgramStatus.ENCERRADO && closedAt == null) {
            throw new DomainRuleException(
                    "REQ-PRG-007", "encerramento exige data de encerramento");
        }

        SocialProgramStatus previous = this.status;
        this.status = newStatus;
        this.statusReason = reason.trim();
        this.closedAt = newStatus == SocialProgramStatus.ENCERRADO ? closedAt : this.closedAt;
        this.updatedAt = clock.instant();
        this.updatedBy = actor.id();
        return previous;
    }

    // -------------------------------------------------------- parametrizacao

    /**
     * Substitui todas as faixas de calculo.
     *
     * <p>Atende {@code REQ-PRG-010}. A nao sobreposicao e invariante do agregado, e nao do
     * banco: verificar sobreposicao exige comparar pares, o que nenhuma restricao
     * declarativa expressa.
     */
    public void replaceBands(ReplaceCalculationBandsCommand command) {
        Objects.requireNonNull(command, "command");
        requireNotClosed();

        if (command.bands().size() > MAX_BANDS) {
            throw new DomainRuleException(
                    "REQ-PRG-010", "o programa admite no maximo " + MAX_BANDS + " faixas");
        }

        List<CalculationBand> candidates = new ArrayList<>();
        for (BandData data : command.bands()) {
            candidates.add(new CalculationBand(
                    this,
                    requirePositiveOrZero(data.incomeFrom(), "REQ-PRG-010", "limite inferior"),
                    data.incomeTo(),
                    Objects.requireNonNull(data.multiplier(), "multiplier"),
                    orZero(data.additionalAmount()),
                    data.accumulates()));
        }

        candidates.sort(Comparator.comparing(CalculationBand::incomeFrom));
        for (int i = 0; i < candidates.size(); i++) {
            CalculationBand current = candidates.get(i);
            current.incomeTo().ifPresent(to -> {
                if (to.compareTo(current.incomeFrom()) <= 0) {
                    throw new DomainRuleException(
                            "REQ-PRG-010", "limite superior deve ser maior que o inferior");
                }
            });
            if (i > 0 && candidates.get(i - 1).overlaps(current)) {
                throw new DomainRuleException("REQ-PRG-010", "faixas de calculo se sobrepoem");
            }
        }

        this.bands.clear();
        this.bands.addAll(candidates);
    }

    /** Atende {@code REQ-PRG-011}. */
    public void replaceRegions(ReplaceRegionalParametersCommand command) {
        Objects.requireNonNull(command, "command");
        requireNotClosed();

        if (command.regions().size() > MAX_REGIONS) {
            throw new DomainRuleException(
                    "REQ-PRG-011", "o programa admite no maximo " + MAX_REGIONS + " regioes");
        }

        Set<String> seen = new HashSet<>();
        List<RegionalParameter> candidates = new ArrayList<>();
        for (RegionData data : command.regions()) {
            String region = data.regionCode() == null ? "" : data.regionCode().trim();
            if (!RegionalParameter.VALID_REGIONS.contains(region)) {
                throw new DomainRuleException(
                        "REQ-PRG-011", "codigo de regiao fora do dominio: " + region);
            }
            if (!seen.add(region)) {
                throw new DomainRuleException(
                        "REQ-PRG-011", "regiao repetida no mesmo programa: " + region);
            }
            candidates.add(new RegionalParameter(
                    this,
                    region,
                    Objects.requireNonNull(data.multiplier(), "multiplier"),
                    orZero(data.complementAmount()),
                    data.active()));
        }

        this.regions.clear();
        this.regions.addAll(candidates);
    }

    // ------------------------------------------------------------- calculo

    /**
     * Fator derivado do ajuste, equivalente a {@code CADPROG.NSP:124}.
     *
     * <p>O resultado e o mesmo do legado. O que muda e que ele deixa de ser aplicado na
     * gravacao do valor base ({@code REQ-PRG-004}) e passa a ser calculado sob demanda.
     */
    public BigDecimal derivedFactor(BigDecimal coefficient) {
        return BigDecimal.ONE.add(adjustmentFactor.multiply(coefficient));
    }

    // ------------------------------------------------------------ validacoes

    private void applyAgeRange(Integer min, Integer max) {
        int resolvedMin = orZero(min);
        int resolvedMax = orZero(max);
        // Zero significa ausencia de limite (SOCPROG.ddm:57-58); faixa invertida so
        // existe quando ambos sao informados.
        if (resolvedMin > 0 && resolvedMax > 0 && resolvedMin > resolvedMax) {
            throw new DomainRuleException(
                    "REQ-PRG-003", "idade minima nao pode ser maior que a maxima");
        }
        this.ageMin = (short) resolvedMin;
        this.ageMax = (short) resolvedMax;
    }

    private void requireNotClosed() {
        if (this.status == SocialProgramStatus.ENCERRADO) {
            throw new DomainRuleException("REQ-PRG-007", "programa encerrado nao aceita alteracao");
        }
    }

    private static String requireCode(String code) {
        String trimmed = trimToNull(code);
        if (trimmed == null || trimmed.length() > 4) {
            throw new DomainRuleException("REQ-PRG-001", "codigo do programa e obrigatorio");
        }
        return trimmed;
    }

    private static String requireName(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            throw new DomainRuleException("REQ-PRG-002", "nome do programa e obrigatorio");
        }
        return trimmed;
    }

    private static SocialProgramType requireType(SocialProgramType type) {
        if (type == null) {
            throw new DomainRuleException("REQ-PRG-002", "tipo do programa e obrigatorio");
        }
        return type;
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new DomainRuleException("REQ-PRG-002", "valor base deve ser maior que zero");
        }
        return amount;
    }

    private static BigDecimal requirePositiveOrZero(BigDecimal value, String requirement, String field) {
        if (value == null || value.signum() < 0) {
            throw new DomainRuleException(requirement, field + " nao pode ser negativo");
        }
        return value;
    }

    // ------------------------------------------------------------- acessores

    public Long id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public Optional<String> acronym() {
        return Optional.ofNullable(acronym);
    }

    public SocialProgramType type() {
        return type;
    }

    public SocialProgramStatus status() {
        return status;
    }

    public Optional<String> statusReason() {
        return Optional.ofNullable(statusReason);
    }

    public BigDecimal amountBase() {
        return amountBase;
    }

    public BigDecimal adjustmentFactor() {
        return adjustmentFactor;
    }

    public Optional<BigDecimal> maxPerCapitaIncome() {
        return Optional.ofNullable(maxPerCapitaIncome);
    }

    public int ageMin() {
        return ageMin;
    }

    public int ageMax() {
        return ageMax;
    }

    public Optional<String> eligibilityCode() {
        return Optional.ofNullable(eligibilityCode);
    }

    public LocalDate startedAt() {
        return startedAt;
    }

    public Optional<LocalDate> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String updatedBy() {
        return updatedBy;
    }

    public List<CalculationBand> bands() {
        return List.copyOf(bands);
    }

    public List<RegionalParameter> regions() {
        return List.copyOf(regions);
    }
    // ------------------------------------------------------------ auxiliares

    private static void recordChange(
            Map<String, Change> changes, String field, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.put(field, new Change(before, after));
        }
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
