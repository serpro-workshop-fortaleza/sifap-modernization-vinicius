package br.gov.sifap.socialprogram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.exception.DomainRuleException;
import br.gov.sifap.socialprogram.ReplaceCalculationBandsCommand.BandData;
import br.gov.sifap.socialprogram.internal.SocialProgramTestFacade;
import br.gov.sifap.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-314 — divergencias deliberadas em relacao ao SIFAP legado.
 *
 * <p>Cada teste falha contra o legado por design e cita o membro Natural cujo
 * comportamento deixa de ser reproduzido.
 */
class LegacyDivergenceSocialProgramIT extends AbstractIntegrationTest {

    private static final Actor OPERATOR = Actor.human("op.ribeiro", "GESTOR");

    @Autowired private SocialProgramTestFacade facade;
    @Autowired private SocialProgramQuery query;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparBase() {
        jdbcTemplate.update("DELETE FROM calculation_band");
        jdbcTemplate.update("DELETE FROM regional_parameter");
        jdbcTemplate.update("DELETE FROM social_program");
    }

    @Test
    @DisplayName("deve gravar o valor base informado em vez do valor ajustado")
    void deve_gravar_o_valor_base_informado() {
        // REQ-PRG-004, nivel C. E o teste central desta fatia.
        // CADPROG.NSP:124 calcula #FACTOR-K, :125 multiplica o valor base por ele e :130
        // grava o resultado no campo AMT-BASE-INDIVIDUAL. Quem consulta o programa ve um
        // numero que ninguem digitou, e o fator fica aplicado duas vezes quando o calculo
        // o aplica de novo.
        facade.register(new RegisterSocialProgramCommand(
                        "0001", "Bolsa Exemplo", null, SocialProgramType.ASSISTENCIA,
                        new BigDecimal("600.00"), new BigDecimal("2.0000"),
                        null, 0, 0, null, null, LocalDate.of(2020, 1, 1)),
                OPERATOR);

        BigDecimal stored = jdbcTemplate.queryForObject(
                "SELECT amount_base FROM social_program WHERE code = ?", BigDecimal.class, "0001");

        assertThat(stored).isEqualByComparingTo("600.00");
        assertThat(facade.derivedFactorOf("0001")).isEqualByComparingTo("1.6944300");
    }

    @Test
    @DisplayName("deve recusar parametros invalidos em vez de grava-los sem verificacao")
    void deve_recusar_parametros_invalidos() {
        // REQ-PRG-002, nivel C. CADPROG.NSP:96-106 captura os parametros e :127-138 os
        // grava, sem uma unica verificacao. 45 registros parametrizam 4,2 milhoes de
        // beneficiarios: cerca de 93 mil pessoas por programa.
        assertThatThrownBy(() -> facade.register(new RegisterSocialProgramCommand(
                                "0001", "Bolsa Exemplo", null, SocialProgramType.ASSISTENCIA,
                                BigDecimal.ZERO, BigDecimal.ZERO, null, 0, 0, null, null,
                                LocalDate.of(2020, 1, 1)),
                        OPERATOR))
                .isInstanceOf(DomainRuleException.class);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM social_program", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("deve recusar faixa etaria invertida em vez de torna-la inelegivel para todos")
    void deve_recusar_faixa_etaria_invertida() {
        // REQ-PRG-003, nivel C. CADPROG.NSP:136-137 grava AGE-MIN e AGE-MAX sem verificar
        // a ordem, e VALELEG.NSN:153-169 nao distingue faixa invertida de regra deliberada.
        assertThatThrownBy(() -> facade.register(new RegisterSocialProgramCommand(
                                "0001", "Bolsa Exemplo", null, SocialProgramType.ASSISTENCIA,
                                new BigDecimal("600.00"), BigDecimal.ZERO, null, 65, 18, null, null,
                                LocalDate.of(2020, 1, 1)),
                        OPERATOR))
                .isInstanceOf(DomainRuleException.class)
                .extracting(e -> ((DomainRuleException) e).requirementId())
                .isEqualTo("REQ-PRG-003");
    }

    @Test
    @DisplayName("deve permitir alterar o programa, operacao que o legado nao tem")
    void deve_permitir_alterar_o_programa() {
        // REQ-PRG-006, nivel C. CADPROG.NSP:80-83 implementa apenas I e C. Um catalogo de
        // parametrizacao sem alteracao nao comporta reajuste anual, que e sua razao de ser.
        facade.register(command("0001"), OPERATOR);
        facade.update(
                "0001",
                new UpdateSocialProgramCommand(
                        "Bolsa Reajustada", null, new BigDecimal("650.00"), null, null, 0, 65, "RD"),
                OPERATOR);

        assertThat(query.findByCode("0001"))
                .get()
                .satisfies(view -> assertThat(view.amountBase()).isEqualByComparingTo("650.00"));
    }

    @Test
    @DisplayName("deve permitir encerrar o programa, situacao que o legado nunca atribui")
    void deve_permitir_encerrar_o_programa() {
        // REQ-PRG-007, nivel C. SOCPROG.ddm:37 declara I e E desde 1997 e CADPROG.NSP:134
        // grava A sempre, de modo que a recusa por programa inativo de VALELEG.NSN:114-118
        // existe e nunca e acionada.
        facade.register(command("0001"), OPERATOR);
        facade.changeStatus(
                "0001",
                new ChangeSocialProgramStatusCommand(
                        SocialProgramStatus.ENCERRADO, "fim do programa", LocalDate.of(2026, 6, 30)),
                OPERATOR);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM social_program WHERE code = ?", String.class, "0001"))
                .isEqualTo("ENCERRADO");
    }

    @Test
    @DisplayName("deve manter as faixas de calculo como dado, e nao como constante no fonte")
    void deve_manter_as_faixas_como_dado() {
        // REQ-PRG-010, nivel C. A estrutura existe em SOCPROG.ddm:69 desde 1997, vazia,
        // enquanto CALCBENF.NSN:129-137 carrega cinco fatores por MOVE no codigo-fonte.
        facade.register(command("0001"), OPERATOR);
        facade.replaceBands(
                "0001",
                new ReplaceCalculationBandsCommand(List.of(
                        new BandData(new BigDecimal("0.00"), new BigDecimal("500.00"),
                                new BigDecimal("1.0000"), BigDecimal.ZERO, false),
                        new BandData(new BigDecimal("500.00"), null,
                                new BigDecimal("0.8500"), BigDecimal.ZERO, false))),
                OPERATOR);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM calculation_band", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("deve recusar regiao fora do dominio no banco")
    void deve_recusar_regiao_fora_do_dominio_no_banco() {
        // REQ-PRG-011, nivel C. A restricao vive no banco, nao so na aplicacao.
        facade.register(command("0001"), OPERATOR);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO regional_parameter
                            (social_program_id, region_code, multiplier, complement_amount, active)
                        SELECT id, '07', 1.0, 0, true FROM social_program WHERE code = ?
                        """,
                        "0001"))
                .hasMessageContaining("ck_region_code");
    }

    @Test
    @DisplayName("deve registrar autor em vez de deixar o campo do dicionario vazio")
    void deve_registrar_autor() {
        // REQ-PRG-013, nivel C. SOCPROG.ddm:95-98 declara DT-INSERT, USR-INSERT,
        // DT-LAST-UPDATE e USR-LAST-UPDATE desde 1997 e nenhum programa os preenche.
        facade.register(command("0001"), OPERATOR);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT created_by FROM social_program WHERE code = ?", String.class, "0001"))
                .isEqualTo("op.ribeiro");
    }

    private static RegisterSocialProgramCommand command(String code) {
        return new RegisterSocialProgramCommand(
                code, "Bolsa Exemplo", "BEX", SocialProgramType.ASSISTENCIA,
                new BigDecimal("600.00"), BigDecimal.ZERO, new BigDecimal("300.00"),
                0, 65, "RD", "Lei 10836", LocalDate.of(2020, 1, 1));
    }
}
