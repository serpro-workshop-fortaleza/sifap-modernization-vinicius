package br.gov.sifap.beneficiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.audit.AuditEventView;
import br.gov.sifap.audit.AuditQuery;
import br.gov.sifap.beneficiary.internal.BeneficiaryTestFacade;
import br.gov.sifap.shared.document.Cpf;
import br.gov.sifap.shared.document.Nis;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.exception.ResourceConflictException;
import br.gov.sifap.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-209 e T-210 — cobre {@code REQ-BEN-001}, {@code REQ-BEN-015} a {@code REQ-BEN-017} e
 * {@code REQ-BEN-019}, alem da integracao com a trilha da Fatia 1.
 */
class BeneficiaryPersistenceIT extends AbstractIntegrationTest {

    private static final Instant FROM = Instant.now().minus(1, ChronoUnit.DAYS);
    private static final Instant TO = Instant.now().plus(1, ChronoUnit.DAYS);
    private static final Actor OPERATOR = Actor.human("op.silva", "SUPERVISOR");
    private static final String CPF = "11144477735";

    @Autowired private BeneficiaryTestFacade facade;
    @Autowired private BeneficiaryQuery query;
    @Autowired private AuditQuery auditQuery;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparBase() {
        jdbcTemplate.update("DELETE FROM beneficiary_migration_issue");
        jdbcTemplate.update("DELETE FROM dependent");
        jdbcTemplate.update("DELETE FROM beneficiary");
        jdbcTemplate.update("DELETE FROM audit_change_event");
        jdbcTemplate.update("DELETE FROM audit_access_event");
    }

    @Test
    @DisplayName("deve gravar o beneficiario e registrar a inclusao na trilha")
    void deve_gravar_e_auditar_a_inclusao() {
        // REQ-BEN-001 e REQ-AUD-001. O contexto publica o evento; quem grava a trilha
        // e a auditoria, sem que o cadastro conheca o modulo de auditoria.
        facade.register(command(CPF), OPERATOR);

        List<AuditEventView> events = auditQuery.findChangesBySubject(CPF, FROM, TO);

        assertThat(events)
                .extracting(AuditEventView::action)
                .containsExactlyInAnyOrder(AuditAction.INCLUSAO);
    }

    @Test
    @DisplayName("deve recusar a inclusao quando o CPF ja existe")
    void deve_recusar_cpf_duplicado() {
        // REQ-BEN-001 / AC-001.1
        facade.register(command(CPF), OPERATOR);

        assertThatThrownBy(() -> facade.register(command(CPF), OPERATOR))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    @DisplayName("deve registrar autor e instante de criacao e de alteracao")
    void deve_registrar_autor_e_instante() {
        // REQ-BEN-019. BENEFIC.ddm:112-118 declara os campos e nenhum programa os preenche.
        facade.register(command(CPF), OPERATOR);
        facade.update(
                CPF,
                new UpdateBeneficiaryCommand("Maria Silva Souza", "0001", BigDecimal.ONE, null, null, null),
                Actor.human("op.costa", "ANALISTA"));

        String createdBy = jdbcTemplate.queryForObject(
                "SELECT created_by FROM beneficiary WHERE cpf = ?", String.class, CPF);
        String updatedBy = jdbcTemplate.queryForObject(
                "SELECT updated_by FROM beneficiary WHERE cpf = ?", String.class, CPF);

        assertThat(createdBy).isEqualTo("op.silva");
        assertThat(updatedBy).isEqualTo("op.costa");
    }

    @Test
    @DisplayName("deve gravar a alteracao com valores anterior e posterior na trilha")
    void deve_gravar_alteracao_com_valores_anterior_e_posterior() {
        // REQ-AUD-006. Primeiro publicador real do contrato criado na Fatia 1.
        facade.register(command(CPF), OPERATOR);
        facade.update(
                CPF,
                new UpdateBeneficiaryCommand(
                        "Maria Silva Souza", "0001", new BigDecimal("1450.00"), null, null, null),
                OPERATOR);

        AuditEventView alteracao = auditQuery.findChangesBySubject(CPF, FROM, TO).stream()
                .filter(event -> event.action() == AuditAction.ALTERACAO)
                .findFirst()
                .orElseThrow();

        assertThat(alteracao.changes()).containsKey("fullName");
        assertThat(alteracao.changes().get("fullName").before()).isEqualTo("Maria Silva");
        assertThat(alteracao.changes().get("fullName").after()).isEqualTo("Maria Silva Souza");
    }

    @Test
    @DisplayName("deve consultar por CPF e por NIS")
    void deve_consultar_por_cpf_e_por_nis() {
        // REQ-BEN-015 / AC-015.1 e AC-015.2
        facade.register(command(CPF), OPERATOR);

        assertThat(query.findByCpf(Cpf.of(CPF), OPERATOR)).isPresent();
        assertThat(query.findByNis(Nis.of("12345678919"), OPERATOR)).isPresent();
    }

    @Test
    @DisplayName("deve registrar o acesso na trilha propria quando a consulta encontra o registro")
    void deve_registrar_acesso_na_trilha_propria() {
        // REQ-BEN-016 / AC-016.1 e REQ-AUD-008: consulta e conciliacao recebem codigos
        // distintos, ao contrario do CO ambiguo do legado.
        facade.register(command(CPF), OPERATOR);
        query.findByCpf(Cpf.of(CPF), OPERATOR);

        assertThat(auditQuery.findAccessesBySubject(CPF, FROM, TO))
                .singleElement()
                .satisfies(event -> assertThat(event.action()).isEqualTo(AuditAction.CONSULTA));
    }

    @Test
    @DisplayName("deve nao registrar acesso quando a consulta nao encontra o registro")
    void deve_nao_registrar_acesso_quando_nao_encontra() {
        // REQ-BEN-016 / AC-016.2. CONSBENF.NSP devolve REINPUT antes da trilha.
        query.findByCpf(Cpf.of("52998224725"), OPERATOR);

        assertThat(auditQuery.findAccessesBySubject("52998224725", FROM, TO)).isEmpty();
    }

    @Test
    @DisplayName("deve devolver o documento mascarado na consulta")
    void deve_devolver_o_documento_mascarado() {
        // REQ-BEN-017. CONSBENF.NSP:200-205 declara HIDE SENSITIVE DATA.
        facade.register(command(CPF), OPERATOR);

        BeneficiaryView view = query.findByCpf(Cpf.of(CPF), OPERATOR).orElseThrow();

        assertThat(view.maskedCpf()).isEqualTo("***.444.777-**").doesNotContain(CPF);
    }

    @Test
    @DisplayName("deve nao deixar beneficiario sem evento quando a transacao e desfeita")
    void deve_nao_deixar_beneficiario_sem_evento_quando_desfeita() {
        // O evento roda em BEFORE_COMMIT: dado e trilha sao desfeitos juntos, como o
        // BACKOUT do legado.
        assertThatThrownBy(() -> facade.registerThenFail(command(CPF), OPERATOR))
                .isInstanceOf(IllegalStateException.class);

        Integer stored = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM beneficiary WHERE cpf = ?", Integer.class, CPF);

        assertThat(stored).isZero();
        assertThat(auditQuery.findChangesBySubject(CPF, FROM, TO)).isEmpty();
    }

    private static RegisterBeneficiaryCommand command(String cpf) {
        return new RegisterBeneficiaryCommand(
                cpf,
                "12345678919",
                "Maria Silva",
                LocalDate.of(1990, 5, 20),
                Sex.F,
                "0001",
                BigDecimal.TEN,
                new AddressData("Rua A", "10", null, "Centro", "Sao Paulo", "SP", "01310100", "35"),
                null,
                null);
    }
}
