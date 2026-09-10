package br.gov.sifap.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.shared.event.ActorType;
import br.gov.sifap.shared.event.AuditAction;
import br.gov.sifap.shared.event.AuditableEventBatch;
import br.gov.sifap.shared.event.Change;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-103, T-105, T-106, T-107, T-108 e T-109.
 *
 * <p>Cobre {@code REQ-AUD-001}, {@code REQ-AUD-004} a {@code REQ-AUD-010}.
 */
@Import(TestEventPublisher.Config.class)
class AuditEventListenerIT extends AbstractAuditIT {

    private static final Instant PERIOD_START = Instant.now().minus(1, ChronoUnit.DAYS);
    private static final Instant PERIOD_END = Instant.now().plus(1, ChronoUnit.DAYS);

    @Autowired private TestEventPublisher publisher;
    @Autowired private AuditQuery auditQuery;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limparTrilha() {
        jdbcTemplate.update("DELETE FROM audit_change_event");
        jdbcTemplate.update("DELETE FROM audit_access_event");
    }

    @Test
    @DisplayName("deve registrar evento quando a transacao de negocio confirma")
    void deve_registrar_evento_quando_a_transacao_confirma() {
        // REQ-AUD-001 / AC-001.1
        publisher.publish(TestAuditEvent.beneficiaryRegistered("11144477735"));

        List<AuditEventView> events =
                auditQuery.findChangesBySubject("11144477735", PERIOD_START, PERIOD_END);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.INCLUSAO);
            assertThat(event.entityType()).isEqualTo("BENEFICIARY");
        });
    }

    @Test
    @DisplayName("deve nao deixar evento orfao quando a transacao de negocio e desfeita")
    void deve_nao_deixar_evento_orfao_quando_a_transacao_e_desfeita() {
        // REQ-AUD-001. Preserva o comportamento do BACKOUT legado, que desfaz dado e
        // auditoria juntos. Este teste falha se o listener virar assincrono.
        assertThatThrownBy(() ->
                        publisher.publishThenFail(
                                TestAuditEvent.beneficiaryRegistered("12345678909")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(auditQuery.findChangesBySubject("12345678909", PERIOD_START, PERIOD_END))
                .isEmpty();
    }

    @Test
    @DisplayName("deve registrar autor e perfil quando o evento e gravado")
    void deve_registrar_autor_e_perfil_quando_o_evento_e_gravado() {
        // REQ-AUD-005 / AC-005.1. No legado o campo de perfil existe desde 2005
        // e nenhum programa o preenche.
        publisher.publish(TestAuditEvent.beneficiaryRegistered("11144477735"));

        AuditEventView event =
                auditQuery.findChangesBySubject("11144477735", PERIOD_START, PERIOD_END).getFirst();

        assertThat(event.actor().id()).isEqualTo("op.silva");
        assertThat(event.actor().profile()).isEqualTo("SUPERVISOR");
        assertThat(event.actor().type()).isEqualTo(ActorType.HUMANO);
    }

    @Test
    @DisplayName("deve identificar o processo quando a operacao nao tem usuario interativo")
    void deve_identificar_o_processo_quando_nao_ha_usuario_interativo() {
        // REQ-AUD-005 / AC-005.2
        publisher.publish(TestAuditEvent.payrollCycleCompleted("CICLO-2026-09", 10));

        AuditEventView event = auditQuery.findChangesByBatchRun("CICLO-2026-09").getFirst();

        assertThat(event.actor().type()).isEqualTo(ActorType.PROCESSO);
        assertThat(event.actor().id()).isEqualTo("BATCHPGT");
    }

    @Test
    @DisplayName("deve preservar a precisao do instante quando dois eventos ocorrem no mesmo segundo")
    void deve_preservar_a_precisao_do_instante() {
        // REQ-AUD-004 / AC-004.1. O legado descarta o decimo de segundo para caber em
        // campo de seis digitos, e a ordem entre eventos do mesmo segundo se perde.
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        publisher.publish(
                TestAuditEvent.beneficiaryRegistered("11144477735").at(base.plusMillis(120)),
                TestAuditEvent.beneficiaryUpdated("11144477735", Map.of("x", Change.of("1", "2")))
                        .at(base.plusMillis(870)));

        List<AuditEventView> events =
                auditQuery.findChangesBySubject("11144477735", PERIOD_START, PERIOD_END);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).occurredAt()).isAfter(events.get(1).occurredAt());
        assertThat(events.get(0).occurredAt().toEpochMilli() % 1000).isEqualTo(870);
    }

    @Test
    @DisplayName("deve registrar valor anterior e posterior quando o evento e de alteracao")
    void deve_registrar_valor_anterior_e_posterior() {
        // REQ-AUD-006 / AC-006.1. A estrutura existe em AUDIT.ddm:61-69 desde 2005
        // e nunca foi preenchida.
        publisher.publish(TestAuditEvent.beneficiaryUpdated(
                "11144477735",
                Map.of(
                        "amtFamilyIncome", Change.of("1200.00", "1450.00"),
                        "codRegion", Change.of("31", "35"))));

        AuditEventView event =
                auditQuery.findChangesBySubject("11144477735", PERIOD_START, PERIOD_END).getFirst();

        assertThat(event.changes())
                .hasSize(2)
                .containsEntry("amtFamilyIncome", Change.of("1200.00", "1450.00"))
                .containsEntry("codRegion", Change.of("31", "35"));
    }

    @Test
    @DisplayName("deve nao registrar valor anterior quando o evento e de inclusao")
    void deve_nao_registrar_valor_anterior_quando_o_evento_e_de_inclusao() {
        // REQ-AUD-006 / AC-006.2
        publisher.publish(TestAuditEvent.beneficiaryRegistered("11144477735"));

        AuditEventView event =
                auditQuery.findChangesBySubject("11144477735", PERIOD_START, PERIOD_END).getFirst();

        assertThat(event.changes()).isEmpty();
    }

    @Test
    @DisplayName("deve permitir consultar por campo alterado quando as mudancas estao em JSONB")
    void deve_permitir_consultar_por_campo_alterado() {
        // Ganho sobre o legado: os grupos MU do Adabas nao suportam esta consulta.
        publisher.publish(TestAuditEvent.beneficiaryUpdated(
                "11144477735", Map.of("amtFamilyIncome", Change.of("1200.00", "1450.00"))));

        Integer matches = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_change_event WHERE jsonb_exists(changes, 'amtFamilyIncome')",
                Integer.class);

        assertThat(matches).isOne();
    }

    @Test
    @DisplayName("deve gravar na trilha de acesso quando o evento e de consulta")
    void deve_gravar_na_trilha_de_acesso_quando_o_evento_e_de_consulta() {
        // REQ-AUD-007 / AC-007.1 e REQ-AUD-008 / AC-008.1: consulta e conciliacao
        // recebem codigos distintos, ao contrario do CO ambiguo do legado.
        publisher.publish(TestAuditEvent.beneficiaryQueried("11144477735"));

        assertThat(auditQuery.findAccessesBySubject("11144477735", PERIOD_START, PERIOD_END))
                .singleElement()
                .satisfies(event -> assertThat(event.action()).isEqualTo(AuditAction.CONSULTA));
        assertThat(auditQuery.findChangesBySubject("11144477735", PERIOD_START, PERIOD_END))
                .isEmpty();
    }

    @Test
    @DisplayName("deve registrar um evento por operacao alem do evento de ciclo")
    void deve_registrar_um_evento_por_operacao_alem_do_evento_de_ciclo() {
        // REQ-AUD-010 / AC-010.1. Hoje 3,8 milhoes de pagamentos por ciclo produzem
        // um unico registro de auditoria (BATCHPGT.NSP:536-546).
        String batchRunId = "CICLO-2026-09";
        List<Object> events = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            events.add(TestAuditEvent.paymentGenerated(
                    "PGTO-" + i, "1114447773" + (i % 10), batchRunId));
        }
        events.add(TestAuditEvent.payrollCycleCompleted(batchRunId, 25));

        publisher.publish(events.toArray());

        List<AuditEventView> recorded = auditQuery.findChangesByBatchRun(batchRunId);

        assertThat(recorded).hasSize(26);
        assertThat(recorded)
                .filteredOn(event -> event.action() == AuditAction.PROCESSAMENTO)
                .hasSize(1);
    }

    @Test
    @DisplayName("deve tornar um pagamento individual rastreavel quando o ciclo e auditado")
    void deve_tornar_um_pagamento_individual_rastreavel() {
        // REQ-AUD-010 / AC-010.2 e REQ-AUD-009 / AC-009.1
        publisher.publish(TestAuditEvent.paymentGenerated("PGTO-7", "11144477735", "CICLO-2026-09"));

        AuditEventView event =
                auditQuery.findChangesByEntity("PAYMENT", "PGTO-7", PERIOD_START, PERIOD_END)
                        .getFirst();

        assertThat(event.batchRunId()).contains("CICLO-2026-09");
        assertThat(event.actor().id()).isEqualTo("BATCHPGT");
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("deve gravar todos os eventos quando o lote e publicado de uma vez")
    void deve_gravar_todos_os_eventos_quando_o_lote_e_publicado() {
        List<br.gov.sifap.shared.event.AuditableEvent> events = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            events.add(TestAuditEvent.paymentGenerated("LOTE-" + i, "11144477735", "CICLO-LOTE"));
        }

        publisher.publish(new AuditableEventBatch(events));

        assertThat(auditQuery.findChangesByBatchRun("CICLO-LOTE")).hasSize(50);
    }
}
