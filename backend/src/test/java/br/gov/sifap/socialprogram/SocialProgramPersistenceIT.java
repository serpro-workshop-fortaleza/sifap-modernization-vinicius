package br.gov.sifap.socialprogram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.audit.AuditEventView;
import br.gov.sifap.audit.AuditQuery;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.exception.ResourceConflictException;
import br.gov.sifap.socialprogram.ReplaceCalculationBandsCommand.BandData;
import br.gov.sifap.socialprogram.ReplaceRegionalParametersCommand.RegionData;
import br.gov.sifap.socialprogram.internal.SocialProgramTestFacade;
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

/** T-310 e T-311 — cobre {@code REQ-PRG-001}, {@code 009}, {@code 012}, {@code 013} e {@code 014}. */
class SocialProgramPersistenceIT extends AbstractIntegrationTest {

    private static final Instant FROM = Instant.now().minus(1, ChronoUnit.DAYS);
    private static final Instant TO = Instant.now().plus(1, ChronoUnit.DAYS);
    private static final Actor OPERATOR = Actor.human("op.ribeiro", "GESTOR");

    @Autowired private SocialProgramTestFacade facade;
    @Autowired private SocialProgramQuery query;
    @Autowired private AuditQuery auditQuery;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparBase() {
        jdbcTemplate.update("DELETE FROM calculation_band");
        jdbcTemplate.update("DELETE FROM regional_parameter");
        jdbcTemplate.update("DELETE FROM social_program");
        jdbcTemplate.update("DELETE FROM audit_change_event");
    }

    @Test
    @DisplayName("deve gravar o programa e registrar a inclusao na trilha")
    void deve_gravar_e_auditar_a_inclusao() {
        facade.register(command("0001"), OPERATOR);

        List<AuditEventView> events =
                auditQuery.findChangesByEntity("SOCIAL_PROGRAM", "0001", FROM, TO);

        assertThat(events)
                .singleElement()
                .satisfies(event -> assertThat(event.action()).isEqualTo(AuditAction.INCLUSAO));
    }

    @Test
    @DisplayName("deve registrar evento sem CPF afetado quando o sujeito nao e pessoa")
    void deve_registrar_evento_sem_cpf_afetado() {
        // REQ-PRG-014 / AC-014.2. CADPROG.NSP:144 faz RESET #AUD-CPF.
        // Primeiro contexto cujos eventos nao tem sujeito pessoal.
        facade.register(command("0001"), OPERATOR);

        AuditEventView event =
                auditQuery.findChangesByEntity("SOCIAL_PROGRAM", "0001", FROM, TO).getFirst();

        assertThat(event.subjectCpf()).isEmpty();
    }

    @Test
    @DisplayName("deve recusar a inclusao quando o codigo ja existe")
    void deve_recusar_codigo_duplicado() {
        facade.register(command("0001"), OPERATOR);

        assertThatThrownBy(() -> facade.register(command("0001"), OPERATOR))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    @DisplayName("deve publicar alteracao com valores anterior e posterior")
    void deve_publicar_alteracao_com_valores() {
        // REQ-PRG-006. Operacao que o legado nao possui.
        facade.register(command("0001"), OPERATOR);
        facade.update(
                "0001",
                new UpdateSocialProgramCommand(
                        "Bolsa Reajustada", null, new BigDecimal("650.00"), null, null, 0, 0, null),
                OPERATOR);

        AuditEventView alteracao =
                auditQuery.findChangesByEntity("SOCIAL_PROGRAM", "0001", FROM, TO).stream()
                        .filter(event -> event.action() == AuditAction.ALTERACAO)
                        .findFirst()
                        .orElseThrow();

        assertThat(alteracao.changes().get("amountBase").before()).isEqualTo("600.00");
        assertThat(alteracao.changes().get("amountBase").after()).isEqualTo("650.00");
    }

    @Test
    @DisplayName("deve publicar a mudanca de situacao quando o programa e encerrado")
    void deve_publicar_mudanca_de_situacao() {
        // REQ-PRG-007. SOCPROG.ddm:37 preve I e E desde 1997 e CADPROG.NSP:134 grava A sempre.
        facade.register(command("0001"), OPERATOR);
        facade.changeStatus(
                "0001",
                new ChangeSocialProgramStatusCommand(
                        SocialProgramStatus.ENCERRADO, "fim do programa", LocalDate.of(2026, 6, 30)),
                OPERATOR);

        SocialProgramView view = query.findByCode("0001").orElseThrow();

        assertThat(view.status()).isEqualTo(SocialProgramStatus.ENCERRADO);
        assertThat(view.closedAt()).contains(LocalDate.of(2026, 6, 30));
    }

    @Test
    @DisplayName("deve expor apenas os parametros que o calculo le")
    void deve_expor_apenas_os_parametros_do_calculo() {
        // REQ-PRG-009 / AC-009.1. Conjunto de VALELEG.NSN:106-111 e CALCBENF.NSN:194-196.
        facade.register(command("0001"), OPERATOR);

        SocialProgramParameters parameters = query.parametersOf("0001").orElseThrow();

        assertThat(parameters.type()).isEqualTo(SocialProgramType.ASSISTENCIA);
        assertThat(parameters.status()).isEqualTo(SocialProgramStatus.ATIVO);
        assertThat(parameters.amountBase()).isEqualByComparingTo("600.00");
        assertThat(parameters.eligibilityCode()).contains("RD");
        assertThat(parameters.maxPerCapitaIncome()).isPresent();
    }

    @Test
    @DisplayName("deve devolver vazio quando o programa consultado nao existe")
    void deve_devolver_vazio_quando_nao_existe() {
        // REQ-PRG-009 / AC-009.2
        assertThat(query.parametersOf("9999")).isEmpty();
        assertThat(query.findByCode("9999")).isEmpty();
    }

    @Test
    @DisplayName("deve filtrar programas por situacao")
    void deve_filtrar_por_situacao() {
        // REQ-PRG-012 / AC-012.1
        facade.register(command("0001"), OPERATOR);
        facade.register(command("0002"), OPERATOR);
        facade.changeStatus(
                "0002",
                new ChangeSocialProgramStatusCommand(SocialProgramStatus.INATIVO, "suspensao", null),
                OPERATOR);

        assertThat(query.findByStatus(SocialProgramStatus.ATIVO))
                .extracting(SocialProgramView::code)
                .containsExactly("0001");
    }

    @Test
    @DisplayName("deve persistir faixas e regioes junto do programa")
    void deve_persistir_faixas_e_regioes() {
        // REQ-PRG-010 e REQ-PRG-011. Estruturas declaradas em 1997 e 2002, sempre vazias.
        facade.register(command("0001"), OPERATOR);
        facade.replaceBands(
                "0001",
                new ReplaceCalculationBandsCommand(List.of(
                        new BandData(new BigDecimal("0.00"), new BigDecimal("500.00"),
                                new BigDecimal("1.0000"), BigDecimal.ZERO, false),
                        new BandData(new BigDecimal("500.00"), null,
                                new BigDecimal("0.8500"), BigDecimal.ZERO, false))),
                OPERATOR);
        facade.replaceRegions(
                "0001",
                new ReplaceRegionalParametersCommand(List.of(
                        new RegionData("01", new BigDecimal("1.3500"), BigDecimal.ZERO, true))),
                OPERATOR);

        SocialProgramView view = query.findByCode("0001").orElseThrow();

        assertThat(view.bands()).hasSize(2);
        assertThat(view.regions()).singleElement()
                .satisfies(region -> assertThat(region.regionCode()).isEqualTo("01"));
    }

    @Test
    @DisplayName("deve calcular o fator derivado com o coeficiente vigente")
    void deve_calcular_o_fator_derivado_com_o_coeficiente_vigente() {
        // REQ-PRG-005. O coeficiente vem da tabela, com origem declarada como desconhecida.
        facade.register(new RegisterSocialProgramCommand(
                        "0003", "Bolsa Com Fator", null, SocialProgramType.ASSISTENCIA,
                        new BigDecimal("600.00"), new BigDecimal("2.0000"),
                        null, 0, 0, null, null, LocalDate.of(2020, 1, 1)),
                OPERATOR);

        assertThat(facade.derivedFactorOf("0003")).isEqualByComparingTo("1.6944300");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT source FROM adjustment_coefficient", String.class))
                .contains("SIFAP-M-04");
    }

    @Test
    @DisplayName("deve registrar autor e instante na inclusao e na alteracao")
    void deve_registrar_autor_e_instante() {
        // REQ-PRG-013
        facade.register(command("0001"), OPERATOR);
        facade.update(
                "0001",
                new UpdateSocialProgramCommand(
                        "Outro Nome", null, new BigDecimal("600.00"), null, null, 0, 0, null),
                Actor.human("op.costa", "ANALISTA"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT created_by FROM social_program WHERE code = ?", String.class, "0001"))
                .isEqualTo("op.ribeiro");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT updated_by FROM social_program WHERE code = ?", String.class, "0001"))
                .isEqualTo("op.costa");
    }

    @Test
    @DisplayName("deve nao deixar programa sem evento quando a transacao e desfeita")
    void deve_nao_deixar_programa_sem_evento() {
        assertThatThrownBy(() -> facade.registerThenFail(command("0001"), OPERATOR))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM social_program", Integer.class))
                .isZero();
        assertThat(auditQuery.findChangesByEntity("SOCIAL_PROGRAM", "0001", FROM, TO)).isEmpty();
    }

    private static RegisterSocialProgramCommand command(String code) {
        return new RegisterSocialProgramCommand(
                code,
                "Bolsa Exemplo",
                "BEX",
                SocialProgramType.ASSISTENCIA,
                new BigDecimal("600.00"),
                BigDecimal.ZERO,
                new BigDecimal("300.00"),
                0,
                65,
                "RD",
                "Lei 10836",
                LocalDate.of(2020, 1, 1));
    }
}
