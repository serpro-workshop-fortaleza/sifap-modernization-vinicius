package br.gov.sifap.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-102 — cobre {@code REQ-AUD-002}.
 *
 * <p>O dicionario legado declara {@code IMMUTABLE RECORD - UPDATE/DELETE NOT ALLOWED}
 * e nada impede a operacao. Aqui a invariante e do banco: a role da aplicacao nao tem
 * o privilegio, e o teste conecta como ela para provar.
 */
class AuditImmutabilityIT extends AbstractAuditIT {

    private static final String APP_USER = "app_tester";

    /** Senha efemera do usuario de teste; nunca sai desta execucao. */
    private static final String APP_PASSWORD = randomPassword();

    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepararUsuarioDaAplicacao() {
        jdbcTemplate.update("DELETE FROM audit_change_event");
        jdbcTemplate.execute(
                "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '"
                        + APP_USER
                        + "') THEN CREATE USER "
                        + APP_USER
                        + " PASSWORD '"
                        + APP_PASSWORD
                        + "'; END IF; END $$");
        jdbcTemplate.execute("GRANT sifap_app TO " + APP_USER);

        jdbcTemplate.update(
                """
                INSERT INTO audit_change_event
                    (occurred_at, action, entity_type, entity_id, subject_cpf,
                     actor_id, actor_profile, actor_type)
                VALUES (?, 'INCLUSAO', 'BENEFICIARY', '11144477735', '11144477735',
                        'op.silva', 'SUPERVISOR', 'HUMANO')
                """,
                java.sql.Timestamp.from(Instant.now()));
    }

    @Test
    @DisplayName("deve recusar a atualizacao quando a role da aplicacao tenta alterar um evento")
    void deve_recusar_a_atualizacao() throws SQLException {
        // AC-002.1
        assertThatThrownBy(() -> executeAsApplication(
                        "UPDATE audit_change_event SET actor_id = 'outro'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permiss");
    }

    @Test
    @DisplayName("deve recusar a exclusao quando a role da aplicacao tenta remover um evento")
    void deve_recusar_a_exclusao() {
        // AC-002.2
        assertThatThrownBy(() -> executeAsApplication("DELETE FROM audit_change_event"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permiss");
    }

    @Test
    @DisplayName("deve recusar o truncate quando a role da aplicacao tenta esvaziar a trilha")
    void deve_recusar_o_truncate() {
        assertThatThrownBy(() -> executeAsApplication("TRUNCATE audit_change_event"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("deve permitir leitura e insercao quando a role da aplicacao opera normalmente")
    void deve_permitir_leitura_e_insercao() throws SQLException {
        // A imutabilidade nao pode custar a operacao: registrar e consultar continuam liberados.
        executeAsApplication("SELECT count(*) FROM audit_change_event");
        executeAsApplication(
                """
                INSERT INTO audit_change_event
                    (occurred_at, action, entity_type, entity_id,
                     actor_id, actor_profile, actor_type)
                VALUES (now(), 'ALTERACAO', 'BENEFICIARY', '12345678909',
                        'op.silva', 'SUPERVISOR', 'HUMANO')
                """);

        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_change_event", Integer.class);

        assertThat(total).isEqualTo(2);
    }

    @Test
    @DisplayName("deve recusar acao de consulta quando gravada na trilha de alteracao")
    void deve_recusar_acao_de_consulta_na_trilha_de_alteracao() {
        // REQ-AUD-008: o roteamento entre as duas tabelas e invariante do banco,
        // nao decisao que o chamador possa contornar.
        assertThatThrownBy(() -> executeAsApplication(
                        """
                        INSERT INTO audit_change_event
                            (occurred_at, action, entity_type, entity_id,
                             actor_id, actor_profile, actor_type)
                        VALUES (now(), 'CONSULTA', 'BENEFICIARY', '12345678909',
                                'op.silva', 'SUPERVISOR', 'HUMANO')
                        """))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_change_action");
    }

    private void executeAsApplication(String sql) throws SQLException {
        try (Connection connection =
                        DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_USER, APP_PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
