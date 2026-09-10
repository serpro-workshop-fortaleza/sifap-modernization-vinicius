package br.gov.sifap.beneficiary;

import static org.assertj.core.api.Assertions.assertThat;

import br.gov.sifap.beneficiary.internal.migration.BeneficiaryLoader;
import br.gov.sifap.beneficiary.internal.migration.DomainInventoryEntry;
import br.gov.sifap.beneficiary.internal.migration.MigrationIssueRepository;
import br.gov.sifap.beneficiary.internal.migration.MigrationIssueType;
import br.gov.sifap.beneficiary.internal.migration.MigrationReport;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-212 e T-213 — cobre {@code REQ-BEN-020} e {@code REQ-BEN-021}.
 *
 * <p>A regra sob teste e uma so: <strong>nenhum beneficiario e descartado</strong>. Uma
 * carga que recusa registro remove a possibilidade de decidir sobre ele, e essa decisao e
 * de negocio.
 */
class BeneficiaryLoaderIT extends AbstractIntegrationTest {

    private static final Actor MIGRATION = Actor.process("CARGA-INICIAL");

    @Autowired private BeneficiaryLoader loader;
    @Autowired private MigrationIssueRepository issues;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparBase() {
        jdbcTemplate.update("DELETE FROM beneficiary_migration_issue");
        jdbcTemplate.update("DELETE FROM dependent");
        jdbcTemplate.update("DELETE FROM beneficiary");
    }

    @Test
    @DisplayName("deve migrar o registro sinalizado quando o CPF se torna invalido")
    void deve_migrar_sinalizado_quando_o_cpf_e_invalido() {
        // REQ-BEN-020 / AC-020.1. O CPF de digitos repetidos e aceito hoje por
        // CADBENEF.NSP:344-413 e recusado pela rotina unificada do ADR-0005.
        MigrationReport report = loader.load(List.of(beneficiaryLine("11111111111", "A")), MIGRATION);

        assertThat(report.beneficiariesMigrated()).isOne();
        assertThat(report.issuesOf(MigrationIssueType.CPF_INVALIDO)).isOne();
        assertThat(migratedCount()).isOne();
    }

    @Test
    @DisplayName("deve migrar com situacao nula quando a origem traz situacao em branco")
    void deve_migrar_com_situacao_nula_quando_a_origem_esta_em_branco() {
        // O branco e o efeito de CADBENEF.NSP:314. Nenhuma atribuicao automatica:
        // decidir se um suspenso reativado esta hoje ativo e decisao de negocio.
        MigrationReport report = loader.load(List.of(beneficiaryLine("11144477735", " ")), MIGRATION);

        assertThat(report.issuesOf(MigrationIssueType.SITUACAO_AUSENTE)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM beneficiary WHERE status IS NULL", Integer.class))
                .isOne();
    }

    @Test
    @DisplayName("deve nao descartar nenhum beneficiario ainda que todos tenham pendencia")
    void deve_nao_descartar_nenhum_beneficiario() {
        // REQ-BEN-020 / AC-020.3
        List<String> lines = List.of(
                beneficiaryLine("11111111111", " "),
                beneficiaryLine("00000000000", "X"),
                beneficiaryLine("11144477735", "A"),
                beneficiaryLine("52998224725", "S"));

        MigrationReport report = loader.load(lines, MIGRATION);

        assertThat(report.beneficiariesRead()).isEqualTo(4);
        assertThat(report.noBeneficiaryDiscarded()).isTrue();
        assertThat(migratedCount()).isEqualTo(4);
        assertThat(report.totalIssues()).isPositive();
    }

    @Test
    @DisplayName("deve migrar com data nula quando a data de nascimento e ilegivel")
    void deve_migrar_com_data_nula_quando_ilegivel() {
        String line = beneficiaryLine("11144477735", "A").substring(0, 72)
                + "00000000"
                + beneficiaryLine("11144477735", "A").substring(80);

        MigrationReport report = loader.load(List.of(line), MIGRATION);

        assertThat(report.beneficiariesMigrated()).isOne();
        assertThat(report.issuesOf(MigrationIssueType.DATA_NASCIMENTO_ILEGIVEL)).isOne();
    }

    @Test
    @DisplayName("deve inventariar os valores de parentesco encontrados com sua contagem")
    void deve_inventariar_os_valores_de_parentesco() {
        // REQ-BEN-021 / AC-021.1. O dicionario declara FI, CJ, NT e TU; CADDEPEN.NSP:152
        // aceita FI, CO, IR e OU. Apenas FI coincide, e nao ha como escolher sem medir.
        List<String> lines = List.of(
                beneficiaryLine("11144477735", "A"),
                dependentLine("11144477735", "Ana Souza", "FI", "A"),
                dependentLine("11144477735", "Bruno Souza", "ZZ", "A"),
                dependentLine("11144477735", "Carla Souza", "ZZ", "A"),
                dependentLine("11144477735", "Davi Souza", "QQ", "A"));

        loader.load(lines, MIGRATION);

        List<DomainInventoryEntry> inventory =
                issues.inventoryOf(MigrationIssueType.PARENTESCO_FORA_DO_DOMINIO);

        assertThat(inventory)
                .extracting(DomainInventoryEntry::value, DomainInventoryEntry::occurrences)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ZZ", 2L),
                        org.assertj.core.groups.Tuple.tuple("QQ", 1L));
    }

    @Test
    @DisplayName("deve migrar o dependente sinalizado quando o valor esta fora do dominio")
    void deve_migrar_o_dependente_sinalizado() {
        // REQ-BEN-021 / AC-021.2: sinalizado, nao recusado.
        loader.load(
                List.of(
                        beneficiaryLine("11144477735", "A"),
                        dependentLine("11144477735", "Ana Souza", "ZZ", "A")),
                MIGRATION);

        Integer dependents = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dependent", Integer.class);

        assertThat(dependents).isOne();
        assertThat(issues.findByIssueType(MigrationIssueType.PARENTESCO_FORA_DO_DOMINIO)).hasSize(1);
    }

    @Test
    @DisplayName("deve sinalizar a situacao do dependente quando a origem nao a preenche")
    void deve_sinalizar_situacao_de_dependente_ausente() {
        // CADDEPEN.NSP:194-202 nunca grava STAT-DEPEND: todo dependente da base tem branco.
        MigrationReport report = loader.load(
                List.of(
                        beneficiaryLine("11144477735", "A"),
                        dependentLine("11144477735", "Ana Souza", "FI", " ")),
                MIGRATION);

        assertThat(report.issuesOf(MigrationIssueType.SITUACAO_DEPENDENTE_AUSENTE)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM dependent", String.class))
                .isEqualTo("ATIVO");
    }

    @Test
    @DisplayName("deve sinalizar quando o titular do dependente nao existe")
    void deve_sinalizar_titular_inexistente() {
        MigrationReport report =
                loader.load(List.of(dependentLine("52998224725", "Ana Souza", "FI", "A")), MIGRATION);

        assertThat(report.dependentsMigrated()).isZero();
        assertThat(report.issuesOf(MigrationIssueType.TITULAR_INEXISTENTE)).isOne();
    }

    private Integer migratedCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM beneficiary", Integer.class);
    }

    /** Layout posicional do arquivo de extracao, tipo 1. */
    private static String beneficiaryLine(String cpf, String status) {
        return "1"
                + pad(cpf, 11)
                + pad("Maria Silva", 60)
                + "19900520"
                + "F"
                + pad(status, 1)
                + pad("0001", 4)
                + pad("12345678919", 11)
                + pad("00000120000", 11)
                + pad("Rua A", 60)
                + pad("Sao Paulo", 40)
                + pad("SP", 2)
                + pad("01310100", 8)
                + pad("35", 2)
                + "20100115";
    }

    /** Layout posicional do arquivo de extracao, tipo 2. */
    private static String dependentLine(String holderCpf, String name, String relation, String status) {
        return "2"
                + pad(holderCpf, 11)
                + pad("", 11)
                + pad(name, 60)
                + "20150301"
                + pad(relation, 2)
                + pad(status, 1)
                + "N";
    }

    private static String pad(String value, int size) {
        String safe = value == null ? "" : value;
        if (safe.length() >= size) {
            return safe.substring(0, size);
        }
        return safe + " ".repeat(size - safe.length());
    }
}
