package br.gov.sifap.beneficiary.internal;

import br.gov.sifap.beneficiary.AddDependentCommand;
import br.gov.sifap.beneficiary.AddressData;
import br.gov.sifap.beneficiary.BeneficiaryStatus;
import br.gov.sifap.beneficiary.DependentStatus;
import br.gov.sifap.beneficiary.RegisterBeneficiaryCommand;
import br.gov.sifap.beneficiary.Relationship;
import br.gov.sifap.beneficiary.Sex;
import br.gov.sifap.beneficiary.UpdateBeneficiaryCommand;
import br.gov.sifap.shared.document.Cpf;
import br.gov.sifap.shared.document.Nis;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.Change;
import br.gov.sifap.shared.exception.DomainRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Raiz do agregado de cadastro.
 *
 * <p>Uma instancia que existe e um beneficiario valido: as fabricas validam na construcao,
 * e nao ha construtor publico. E a mesma tecnica dos value objects {@link Cpf} e
 * {@link Nis} do kernel, e e o que atende o {@code AC-003.3} sem codigo adicional.
 *
 * <p>Corrige o padrao que organiza toda esta fatia — validar e ignorar o resultado.
 * {@code CADBENEF.NSP:161} chama {@code SUBVALCP} e {@code :164} examina a variavel da
 * copia interna; {@code :263} chama {@code VALBENEF} e {@code :266} apenas escreve um
 * aviso na tela.
 */
@Entity
@Table(name = "beneficiary")
public class Beneficiary {

    /** Limite efetivo do legado: {@code CADDEPEN.NSP:117} testa {@code > 5} e aceita o sexto. */
    static final int MAX_ACTIVE_DEPENDENTS = 6;

    /** Idade de suspensao automatica, de {@code CADBENEF.NSP:250}. */
    static final int AUTOMATIC_SUSPENSION_AGE = 75;

    private static final int MINIMUM_BIRTH_YEAR = 1900;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "beneficiarySeq")
    @SequenceGenerator(name = "beneficiarySeq", sequenceName = "beneficiary_id_seq", allocationSize = 500)
    private Long id;

    @Column(name = "cpf", nullable = false, updatable = false, length = 11)
    private String cpf;

    @Column(name = "nis", length = 11)
    private String nis;

    @Column(name = "full_name", nullable = false, length = 60)
    private String fullName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 1)
    private Sex sex;

    /** Nulo apenas em registro migrado sem situacao confiavel na origem. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 12)
    private BeneficiaryStatus status;

    @Column(name = "status_reason", length = 60)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "program_code", length = 4)
    private String programCode;

    @Column(name = "family_income", precision = 11, scale = 2)
    private BigDecimal familyIncome;

    @Embedded
    private Address address;

    @Column(name = "phone_mobile", length = 15)
    private String phoneMobile;

    @Column(name = "rg_number", length = 15)
    private String rgNumber;

    @Column(name = "registered_at", nullable = false)
    private LocalDate registeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    /** Campo que {@code BENEFIC.ddm:118} declara desde 1997 e nenhum programa utiliza. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "beneficiary", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Dependent> dependents = new ArrayList<>();

    protected Beneficiary() {
        // exigido pelo JPA
    }

    // ---------------------------------------------------------------- criacao

    public static Beneficiary register(RegisterBeneficiaryCommand command, Actor actor, Clock clock) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(actor, "actor");

        Beneficiary beneficiary = new Beneficiary();
        beneficiary.cpf = requireValidCpf(command.cpf());
        beneficiary.nis = parseOptionalNis(command.nis());
        beneficiary.fullName = requireValidName(command.fullName());
        beneficiary.birthDate = requireValidBirthDate(command.birthDate(), LocalDate.now(clock));
        beneficiary.sex = requireAcceptedSex(command.sex());
        beneficiary.programCode = trimToNull(command.programCode());
        beneficiary.familyIncome = command.familyIncome();
        beneficiary.address = requireValidAddress(command.address());
        beneficiary.phoneMobile = trimToNull(command.phoneMobile());
        beneficiary.rgNumber = trimToNull(command.rgNumber());

        Instant now = clock.instant();
        beneficiary.registeredAt = LocalDate.now(clock);
        beneficiary.createdAt = now;
        beneficiary.createdBy = actor.id();
        beneficiary.updatedAt = now;
        beneficiary.updatedBy = actor.id();

        // Situacao inicial preservada de CADBENEF.NSP:246.
        beneficiary.status = BeneficiaryStatus.ATIVO;
        beneficiary.statusChangedAt = now;
        return beneficiary;
    }

    // ------------------------------------------------------------- alteracao

    /**
     * Constroi um beneficiario a partir de dado ja extraido do legado, sem validar.
     *
     * <p>Atende {@code REQ-BEN-020}. Validar aqui significaria recusar, e recusar
     * significaria descartar. Um registro que nao satisfaz as regras novas entra assim
     * mesmo, sinalizado: decidir o destino dele e competencia de negocio.
     *
     * <p>Este e o unico caminho de criacao que nao valida, e existe apenas para a carga.
     */
    public static Beneficiary migrated(MigratedBeneficiary data, Actor actor, Clock clock) {
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.cpf = data.cpf();
        beneficiary.nis = data.nis();
        beneficiary.fullName = data.fullName();
        beneficiary.birthDate = data.birthDate();
        beneficiary.sex = data.sex();
        beneficiary.status = data.status();
        beneficiary.statusReason = data.status() == null ? null : "migrado do legado";
        beneficiary.programCode = data.programCode();
        beneficiary.familyIncome = data.familyIncome();
        beneficiary.address = Address.of(data.address());
        beneficiary.registeredAt = data.registeredAt();

        Instant now = clock.instant();
        beneficiary.createdAt = now;
        beneficiary.createdBy = actor.id();
        beneficiary.updatedAt = now;
        beneficiary.updatedBy = actor.id();
        beneficiary.statusChangedAt = data.status() == null ? null : now;
        return beneficiary;
    }

    /** Acrescenta dependente vindo da carga, sem aplicar limite nem validacao. */
    public Dependent addMigratedDependent(
            String cpf,
            String fullName,
            LocalDate birthDate,
            Relationship relation,
            DependentStatus status,
            boolean disability) {
        Dependent dependent =
                new Dependent(this, cpf, fullName, birthDate, relation, status, disability);
        this.dependents.add(dependent);
        return dependent;
    }

    /**
     * Aplica a alteracao cadastral e devolve os campos modificados.
     *
     * <p>Atende {@code REQ-BEN-005}: a situacao cadastral nao e tocada. O comando nem
     * possui o campo, de modo que nao existe caminho para o valor chegar.
     *
     * @return campos alterados, com valor anterior e posterior, para {@code REQ-AUD-006}
     */
    public Map<String, Change> update(UpdateBeneficiaryCommand command, Actor actor, Clock clock) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(actor, "actor");

        Map<String, Change> changes = new LinkedHashMap<>();
        String newName = requireValidName(command.fullName());
        Address newAddress = requireValidAddress(command.address());

        recordChange(changes, "fullName", this.fullName, newName);
        recordChange(changes, "programCode", this.programCode, trimToNull(command.programCode()));
        recordChange(changes, "familyIncome", text(this.familyIncome), text(command.familyIncome()));
        recordChange(changes, "phoneMobile", this.phoneMobile, trimToNull(command.phoneMobile()));
        recordChange(changes, "rgNumber", this.rgNumber, trimToNull(command.rgNumber()));
        recordChange(changes, "uf", this.address == null ? null : this.address.uf(), newAddress.uf());

        this.fullName = newName;
        this.programCode = trimToNull(command.programCode());
        this.familyIncome = command.familyIncome();
        this.address = newAddress;
        this.phoneMobile = trimToNull(command.phoneMobile());
        this.rgNumber = trimToNull(command.rgNumber());
        this.updatedAt = clock.instant();
        this.updatedBy = actor.id();
        return changes;
    }

    /** Atende {@code REQ-BEN-004} e {@code REQ-BEN-006}. */
    public BeneficiaryStatus changeStatus(
            BeneficiaryStatus newStatus, String reason, Actor actor, Clock clock) {
        Objects.requireNonNull(newStatus, "newStatus");
        if (reason == null || reason.isBlank()) {
            throw new DomainRuleException("REQ-BEN-006", "motivo da mudanca de situacao e obrigatorio");
        }

        BeneficiaryStatus previous = this.status;
        this.status = newStatus;
        this.statusReason = reason.trim();
        this.statusChangedAt = clock.instant();
        this.updatedAt = clock.instant();
        this.updatedBy = actor.id();
        return previous;
    }

    /**
     * Aplica a suspensao automatica por idade, se couber.
     *
     * <p>Atende {@code REQ-BEN-006} e {@code REQ-BEN-007}. Diferente do legado, so age
     * sobre beneficiario ativo: {@code CADBENEF.NSP:251} atribui {@code S} qualquer que
     * seja a situacao anterior, de modo que alterar o cadastro de um cancelado com mais de
     * 75 anos o promove a suspenso.
     *
     * @return situacao anterior quando a suspensao foi aplicada
     */
    public Optional<BeneficiaryStatus> applyAgeBasedSuspension(Actor actor, Clock clock) {
        OptionalInt age = ageAt(LocalDate.now(clock));
        if (this.status != BeneficiaryStatus.ATIVO
                || age.isEmpty()
                || age.getAsInt() <= AUTOMATIC_SUSPENSION_AGE) {
            return Optional.empty();
        }
        return Optional.of(changeStatus(
                BeneficiaryStatus.SUSPENSO,
                "suspensao automatica acima de " + AUTOMATIC_SUSPENSION_AGE + " anos",
                actor,
                clock));
    }

    // ------------------------------------------------------------ dependentes

    /** Atende {@code REQ-BEN-008}, {@code REQ-BEN-012}, {@code REQ-BEN-013} e {@code REQ-BEN-014}. */
    public Dependent addDependent(AddDependentCommand command) {
        Objects.requireNonNull(command, "command");

        if (this.status == BeneficiaryStatus.CANCELADO || this.status == BeneficiaryStatus.DESLIGADO) {
            throw new DomainRuleException(
                    "REQ-BEN-014", "titular cancelado ou desligado nao aceita dependente");
        }
        if (activeDependentCount() >= MAX_ACTIVE_DEPENDENTS) {
            throw new DomainRuleException(
                    "REQ-BEN-008", "titular ja possui " + MAX_ACTIVE_DEPENDENTS + " dependentes ativos");
        }

        String name = requireValidDependentName(command.fullName());
        Relationship relation = Objects.requireNonNull(command.relation(), "relation");
        String dependentCpf = parseOptionalDependentCpf(command.cpf());

        if (dependentCpf != null && hasDependentWithCpf(dependentCpf)) {
            throw new DomainRuleException(
                    "REQ-BEN-012", "CPF ja vinculado como dependente deste titular");
        }

        Dependent dependent = new Dependent(
                this,
                dependentCpf,
                name,
                command.birthDate(),
                relation,
                DependentStatus.ATIVO,
                command.disability());
        this.dependents.add(dependent);
        return dependent;
    }

    /**
     * Atende {@code REQ-BEN-009}: derivada, nunca armazenada.
     *
     * <p>O legado mantem {@code CK QTY-DEPEND} como contador incrementado sem consultar
     * situacao ({@code CADDEPEN.NSP:192}) e ainda o usa como limite do laco de duplicidade
     * ({@code :176}), de modo que um contador defasado deixa duplicatas passarem.
     */
    public int activeDependentCount() {
        return (int) dependents.stream().filter(Dependent::isActive).count();
    }

    /** Percorre todos os dependentes, e nao um contador. */
    private boolean hasDependentWithCpf(String candidate) {
        return dependents.stream()
                .map(Dependent::cpf)
                .flatMap(Optional::stream)
                .anyMatch(candidate::equals);
    }

    // ------------------------------------------------------------------ idade

    /**
     * Atende {@code REQ-BEN-007}, nivel {@code PS}.
     *
     * <p>Diferenca de anos civis, como {@code CADBENEF.NSP:242}: a idade muda em 1o de
     * janeiro, nao no aniversario, com margem de ate onze meses. Preservado porque altera
     * quem e suspenso, e portanto quem recebe.
     *
     * <p>Vazio quando a origem nao trouxe data legivel, o que so ocorre em registro migrado.
     */
    public OptionalInt ageAt(LocalDate reference) {
        return birthDate == null
                ? OptionalInt.empty()
                : OptionalInt.of(reference.getYear() - birthDate.getYear());
    }

    // ------------------------------------------------------------- validacoes

    private static String requireValidCpf(String rawCpf) {
        return Cpf.tryParse(rawCpf)
                .map(Cpf::value)
                .orElseThrow(() -> new DomainRuleException("REQ-BEN-003", "CPF invalido"));
    }

    private static String parseOptionalNis(String rawNis) {
        if (rawNis == null || rawNis.isBlank()) {
            return null;
        }
        return Nis.tryParse(rawNis)
                .map(Nis::value)
                .orElseThrow(() -> new DomainRuleException("REQ-BEN-003", "NIS invalido"));
    }

    private static String parseOptionalDependentCpf(String rawCpf) {
        if (rawCpf == null || rawCpf.isBlank() || rawCpf.trim().chars().allMatch(c -> c == '0')) {
            // A ausencia passa a ser ausencia; o legado a representa por onze zeros.
            return null;
        }
        return Cpf.tryParse(rawCpf)
                .map(Cpf::value)
                .orElseThrow(() -> new DomainRuleException("REQ-BEN-013", "CPF de dependente invalido"));
    }

    private static String requireValidName(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            throw new DomainRuleException("REQ-BEN-002", "nome e obrigatorio");
        }
        // VALBENEF.NSN:316-331 aproxima "nome e sobrenome" por ao menos um espaco interno.
        if (!trimmed.contains(" ")) {
            throw new DomainRuleException("REQ-BEN-003", "nome deve conter nome e sobrenome");
        }
        return trimmed;
    }

    private static String requireValidDependentName(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            throw new DomainRuleException("REQ-BEN-002", "nome do dependente e obrigatorio");
        }
        return trimmed;
    }

    private static LocalDate requireValidBirthDate(LocalDate birthDate, LocalDate today) {
        if (birthDate == null) {
            throw new DomainRuleException("REQ-BEN-002", "data de nascimento e obrigatoria");
        }
        // Faixa de VALBENEF.NSN:302, que compara apenas o ano.
        if (birthDate.getYear() < MINIMUM_BIRTH_YEAR || birthDate.getYear() > today.getYear()) {
            throw new DomainRuleException("REQ-BEN-003", "data de nascimento fora da faixa aceita");
        }
        return birthDate;
    }

    private static Sex requireAcceptedSex(Sex sex) {
        if (sex == null || !sex.acceptedOnRegistration()) {
            throw new DomainRuleException("REQ-BEN-002", "sexo deve ser M ou F");
        }
        return sex;
    }

    private static Address requireValidAddress(AddressData data) {
        if (data != null && !Address.isValidUf(data.uf())) {
            throw new DomainRuleException("REQ-BEN-003", "unidade federativa invalida");
        }
        return Address.of(data);
    }

    // ------------------------------------------------------------- acessores

    public Long id() {
        return id;
    }

    public String cpf() {
        return cpf;
    }

    public Optional<String> nis() {
        return Optional.ofNullable(nis);
    }

    public String fullName() {
        return fullName;
    }

    public LocalDate birthDate() {
        return birthDate;
    }

    public Sex sex() {
        return sex;
    }

    public Optional<BeneficiaryStatus> status() {
        return Optional.ofNullable(status);
    }

    public Optional<String> statusReason() {
        return Optional.ofNullable(statusReason);
    }

    public Optional<String> programCode() {
        return Optional.ofNullable(programCode);
    }

    public Optional<BigDecimal> familyIncome() {
        return Optional.ofNullable(familyIncome);
    }

    public AddressData address() {
        return address == null ? Address.of(null).toData() : address.toData();
    }

    public Optional<String> phoneMobile() {
        return Optional.ofNullable(phoneMobile);
    }

    public Optional<String> rgNumber() {
        return Optional.ofNullable(rgNumber);
    }

    public LocalDate registeredAt() {
        return registeredAt;
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

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    public List<Dependent> dependents() {
        return List.copyOf(dependents);
    }

    // ------------------------------------------------------------- auxiliares

    private static void recordChange(
            Map<String, Change> changes, String field, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.put(field, new Change(before, after));
        }
    }

    private static String text(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
