package br.gov.sifap.beneficiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.beneficiary.internal.BeneficiaryTestFacade;
import br.gov.sifap.shared.document.Cpf;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.exception.DomainRuleException;
import br.gov.sifap.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-214 — divergencias deliberadas em relacao ao SIFAP legado.
 *
 * <p>Estes testes <strong>falham contra o legado por design</strong>. Cada um cita o
 * membro Natural cujo comportamento deixa de ser reproduzido, e cada um corresponde a um
 * requisito de nivel {@code C} do ADR-0003.
 */
class LegacyDivergenceBeneficiaryIT extends AbstractIntegrationTest {

    private static final Actor OPERATOR = Actor.human("op.silva", "SUPERVISOR");
    private static final String CPF = "11144477735";

    @Autowired private BeneficiaryTestFacade facade;
    @Autowired private BeneficiaryQuery query;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparBase() {
        jdbcTemplate.update("DELETE FROM beneficiary_migration_issue");
        jdbcTemplate.update("DELETE FROM dependent");
        jdbcTemplate.update("DELETE FROM beneficiary");
    }

    @Test
    @DisplayName("deve preservar a suspensao quando dados cadastrais sao alterados")
    void deve_preservar_a_suspensao_quando_dados_cadastrais_sao_alterados() {
        // REQ-BEN-005, nivel C. E o teste central desta fatia.
        // CADBENEF.NSP:314 grava o conteudo residual de #STATUS sobre a situacao vigente,
        // e a tela nem possui campo de situacao. VALELEG.NSN:133-151 testa NE 'A' e depois
        // compara com S, C, D e I: o branco resultante nao casa com nenhum e sai sem
        // alterar a elegibilidade. Alterar o endereco de um suspenso o reativa.
        facade.register(command(CPF), OPERATOR);
        facade.changeStatus(
                CPF,
                new ChangeBeneficiaryStatusCommand(BeneficiaryStatus.SUSPENSO, "revisao cadastral"),
                OPERATOR);

        facade.update(
                CPF,
                new UpdateBeneficiaryCommand("Maria Silva Souza", "0001", BigDecimal.ONE, null, null, null),
                OPERATOR);

        BeneficiaryView view = query.findByCpf(Cpf.of(CPF), OPERATOR).orElseThrow();

        assertThat(view.status()).contains(BeneficiaryStatus.SUSPENSO);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM beneficiary WHERE cpf = ?", String.class, CPF))
                .isEqualTo("SUSPENSO");
    }

    @Test
    @DisplayName("deve recusar a gravacao quando a validacao cadastral aponta inconsistencia")
    void deve_recusar_a_gravacao_quando_a_validacao_aponta_inconsistencia() {
        // REQ-BEN-003, nivel C. CADBENEF.NSP:263 chama VALBENEF e :266 apenas escreve um
        // aviso na tela; :161 chama SUBVALCP e :164 examina a variavel da copia interna.
        // O ticket 6620/2011 declara "pending Benefits Department review" desde 2011.
        assertThatThrownBy(() -> facade.register(
                        new RegisterBeneficiaryCommand(
                                "11111111111", null, "Maria Silva", LocalDate.of(1990, 5, 20),
                                Sex.F, "0001", BigDecimal.TEN, null, null, null),
                        OPERATOR))
                .isInstanceOf(DomainRuleException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM beneficiary", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("deve recusar dependente com CPF invalido em vez de aceita-lo em silencio")
    void deve_recusar_dependente_com_cpf_invalido() {
        // REQ-BEN-013, nivel C. CADDEPEN.NSP:170 monta a mensagem 'INVALID DEPENDENT CPF'
        // em #MSG, nunca a exibe e nao liga #ERR. O comentario declara "warning mode", mas
        // nao ha sequer aviso: o dependente entra na base em silencio.
        facade.register(command(CPF), OPERATOR);

        assertThatThrownBy(() -> facade.addDependent(
                        CPF,
                        new AddDependentCommand(
                                "11111111111", "Ana Souza", LocalDate.of(2015, 3, 1),
                                Relationship.FI, false),
                        OPERATOR))
                .isInstanceOf(DomainRuleException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM dependent", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("deve exigir situacao do dependente em vez de grava-lo em branco")
    void deve_exigir_situacao_do_dependente() {
        // REQ-BEN-010, nivel C. CADDEPEN.NSP:194-202 grava o dependente sem tocar
        // STAT-DEPEND, que o dicionario declara como dominio fixo em BENEFIC.ddm:93.
        facade.register(command(CPF), OPERATOR);
        facade.addDependent(
                CPF,
                new AddDependentCommand(null, "Ana Souza", LocalDate.of(2015, 3, 1), Relationship.FI, false),
                OPERATOR);

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM dependent", String.class))
                .isEqualTo("ATIVO");
    }

    @Test
    @DisplayName("deve barrar CPF de dependente duplicado no banco, e nao so na aplicacao")
    void deve_barrar_cpf_de_dependente_duplicado_no_banco() {
        // REQ-BEN-012, nivel C / AC-012.2. CADDEPEN.NSP:176 percorre a duplicidade ate
        // QTY-DEPEND, contador que :192 incrementa sem consultar situacao: defasado,
        // deixa duplicatas passarem. Aqui a restricao vive no banco.
        facade.register(command(CPF), OPERATOR);
        facade.addDependent(
                CPF,
                new AddDependentCommand("52998224725", "Ana Souza", null, Relationship.FI, false),
                OPERATOR);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO dependent (beneficiary_id, cpf, full_name, relation, status, disability)
                        SELECT id, '52998224725', 'Fraude', 'FI', 'ATIVO', false
                        FROM beneficiary WHERE cpf = ?
                        """,
                        CPF))
                .hasMessageContaining("uq_dependent_cpf");
    }

    @Test
    @DisplayName("deve preservar o endereco integralmente em vez de trunca-lo")
    void deve_preservar_o_endereco_integralmente() {
        // REQ-BEN-018, nivel C. CADBENEF.NSP:277-279 grava um campo A80 no A60
        // STREET-ADDRESS e perde os ultimos 20 bytes desde o ticket 4471/2003.
        String longStreet = "Avenida Presidente Getulio Dornelles Vargas Filho Junior 1234";
        facade.register(
                new RegisterBeneficiaryCommand(
                        CPF, null, "Maria Silva", LocalDate.of(1990, 5, 20), Sex.F, "0001",
                        BigDecimal.TEN,
                        new AddressData(longStreet, "1000", "Bloco B", "Centro", "Sao Paulo", "SP", "01310100", "35"),
                        null, null),
                OPERATOR);

        String stored = jdbcTemplate.queryForObject(
                "SELECT street FROM beneficiary WHERE cpf = ?", String.class, CPF);

        assertThat(stored).isEqualTo(longStreet).hasSizeGreaterThan(60);
    }

    @Test
    @DisplayName("deve registrar autor em vez de deixar o campo do dicionario vazio")
    void deve_registrar_autor() {
        // REQ-BEN-019, nivel C. BENEFIC.ddm:114-117 declara USR-INSERT e USR-LAST-UPDATE
        // desde 1997 e nenhum programa os preenche.
        facade.register(command(CPF), OPERATOR);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT created_by FROM beneficiary WHERE cpf = ?", String.class, CPF))
                .isEqualTo("op.silva");
    }

    private static RegisterBeneficiaryCommand command(String cpf) {
        return new RegisterBeneficiaryCommand(
                cpf,
                null,
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
