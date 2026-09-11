package br.gov.sifap.payment.internal.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.audit.AuditEventView;
import br.gov.sifap.audit.AuditQuery;
import br.gov.sifap.beneficiary.AddressData;
import br.gov.sifap.beneficiary.RegisterBeneficiaryCommand;
import br.gov.sifap.beneficiary.Sex;
import br.gov.sifap.beneficiary.internal.BeneficiaryTestFacade;
import br.gov.sifap.payment.PaymentQuery;
import br.gov.sifap.payment.PaymentStatus;
import br.gov.sifap.payment.PaymentView;
import br.gov.sifap.payment.PayrollCycleResult;
import br.gov.sifap.payment.ReconciliationResult;
import br.gov.sifap.payment.ReconciliationStatus;
import br.gov.sifap.payment.internal.BankRemittanceService;
import br.gov.sifap.payment.internal.PaymentPartitionMaintenance;
import br.gov.sifap.payment.internal.PayrollCycleService;
import br.gov.sifap.shared.document.Cpf;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.exception.DomainRuleException;
import br.gov.sifap.socialprogram.RegisterSocialProgramCommand;
import br.gov.sifap.socialprogram.ReplaceCalculationBandsCommand;
import br.gov.sifap.socialprogram.ReplaceCalculationBandsCommand.BandData;
import br.gov.sifap.socialprogram.ReplaceRegionalParametersCommand;
import br.gov.sifap.socialprogram.ReplaceRegionalParametersCommand.RegionData;
import br.gov.sifap.socialprogram.SocialProgramType;
import br.gov.sifap.socialprogram.internal.SocialProgramTestFacade;
import br.gov.sifap.support.AbstractIntegrationTest;
import br.gov.sifap.support.CpfGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("Ciclo de conciliacao bancaria")
class ReconciliationIT extends AbstractIntegrationTest {

    private static final Actor OPERATOR = Actor.human("op-conc", "SUPERVISOR");
    private static final Actor PROCESS = Actor.process("BATCHCON");
    private static final AtomicInteger PROGRAM_SEQUENCE = new AtomicInteger(2000);
    private static final String PERIOD = "202504";

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private PayrollCycleService cycleService;

    @Autowired
    private BankRemittanceService remittanceService;

    @Autowired
    private PaymentQuery paymentQuery;

    @Autowired
    private AuditQuery auditQuery;

    @Autowired
    private BeneficiaryTestFacade beneficiaries;

    @Autowired
    private SocialProgramTestFacade programs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String programCode;

    @BeforeEach
    void prepare() {
        new PaymentPartitionMaintenance(jdbcTemplate, Clock.systemUTC()).ensurePartition(PERIOD);
        programCode = String.valueOf(PROGRAM_SEQUENCE.incrementAndGet());

        programs.register(
                new RegisterSocialProgramCommand(
                        programCode, "Programa de conciliacao", "PCO", SocialProgramType.ASSISTENCIA,
                        new BigDecimal("1000.00"), BigDecimal.ZERO, null, 0, 0, null,
                        "LEI 1/2025", LocalDate.of(2025, 1, 1)),
                OPERATOR);
        programs.replaceRegions(
                programCode,
                new ReplaceRegionalParametersCommand(
                        List.of(new RegionData("01", new BigDecimal("1.0000"), BigDecimal.ZERO, true))),
                OPERATOR);
        programs.replaceBands(
                programCode,
                new ReplaceCalculationBandsCommand(List.of(new BandData(
                        BigDecimal.ZERO, new BigDecimal("9999.99"), new BigDecimal("1.0000"),
                        BigDecimal.ZERO, false))),
                OPERATOR);
    }

    private String registerBeneficiary() {
        String cpf = CpfGenerator.next();
        beneficiaries.register(
                new RegisterBeneficiaryCommand(
                        cpf, null, "Beneficiario de teste", LocalDate.of(1990, 5, 20), Sex.F,
                        programCode, new BigDecimal("100.00"),
                        new AddressData("Rua A", "10", null, "Centro", "Brasilia", "DF", "70000000", "01"),
                        null, null),
                OPERATOR);
        return cpf;
    }

    /** Gera a folha, emite a remessa e devolve os pagamentos prontos para conciliar. */
    private List<PaymentView> payrollAndRemittance(int beneficiaryCount) {
        List<String> cpfs = new ArrayList<>();
        for (int index = 0; index < beneficiaryCount; index++) {
            cpfs.add(registerBeneficiary());
        }
        PayrollCycleResult cycle = cycleService.run(programCode, PERIOD, PROCESS);
        remittanceService.issue(cycle.cycleId(), PERIOD, PROCESS);

        return cpfs.stream()
                .map(cpf -> paymentQuery.findByCpfAndPeriod(Cpf.of(cpf), PERIOD, OPERATOR).orElseThrow())
                .toList();
    }

    private static String line(String cpf, String documentNumber, String amountCents, String returnCode) {
        char[] buffer = new char[240];
        java.util.Arrays.fill(buffer, ' ');
        put(buffer, 1, "001");
        put(buffer, 8, "3");
        put(buffer, 44, cpf);
        put(buffer, 74, String.format("%010d", Long.parseLong(documentNumber)));
        put(buffer, 120, String.format("%015d", Long.parseLong(amountCents)));
        put(buffer, 140, "20250410");
        put(buffer, 231, returnCode);
        return new String(buffer);
    }

    private static void put(char[] buffer, int start, String value) {
        value.getChars(0, value.length(), buffer, start - 1);
    }

    private static String cents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.DOWN).toPlainString();
    }

    @Test
    @DisplayName("deve confirmar o pagamento quando o retorno traz credito efetuado")
    void deve_confirmar_o_pagamento_quando_o_retorno_traz_credito_efetuado() {
        PaymentView payment = payrollAndRemittance(1).get(0);
        String cpf = unmasked(payment);

        ReconciliationResult result = reconciliationService.reconcile(
                "RET202504.TXT", PERIOD,
                List.of(line(cpf, payment.paymentId(), cents(payment.amountNet()), "00")),
                PROCESS);

        assertThat(result.reconciled()).isEqualTo(1);
        assertThat(result.pending()).isZero();

        PaymentView updated = paymentQuery.findByCpfAndPeriod(Cpf.of(cpf), PERIOD, OPERATOR).orElseThrow();
        assertThat(updated.status()).isEqualTo(PaymentStatus.CONFIRMADO);
        assertThat(updated.reconciliationStatus()).contains(ReconciliationStatus.CONCILIADO);
    }

    /**
     * {@code REQ-REC-009}. No legado a divergencia so existe na trilha, e o pagamento fica
     * na situacao anterior sem marca nenhuma.
     */
    @Test
    @DisplayName("deve marcar divergencia preservando a situacao quando o valor do banco difere")
    void deve_marcar_divergencia_preservando_a_situacao_quando_o_valor_do_banco_difere() {
        PaymentView payment = payrollAndRemittance(1).get(0);
        String cpf = unmasked(payment);
        BigDecimal bankAmount = payment.amountNet().subtract(new BigDecimal("50.00"));

        ReconciliationResult result = reconciliationService.reconcile(
                "RET202504.TXT", PERIOD,
                List.of(line(cpf, payment.paymentId(), cents(bankAmount), "00")),
                PROCESS);

        assertThat(result.divergent()).isEqualTo(1);
        assertThat(result.reconciled()).isZero();

        PaymentView updated = paymentQuery.findByCpfAndPeriod(Cpf.of(cpf), PERIOD, OPERATOR).orElseThrow();
        assertThat(updated.reconciliationStatus()).contains(ReconciliationStatus.DIVERGENTE);
        assertThat(updated.amountReconciled()).contains(bankAmount);
        assertThat(updated.status()).isEqualTo(PaymentStatus.EMITIDO);
    }

    /** {@code REQ-REC-006}: a tolerancia de um centavo permanece, e cada uso fica contavel. */
    @Test
    @DisplayName("deve conciliar e contar a tolerancia quando a diferenca e de um centavo")
    void deve_conciliar_e_contar_a_tolerancia_quando_a_diferenca_e_de_um_centavo() {
        PaymentView payment = payrollAndRemittance(1).get(0);
        String cpf = unmasked(payment);
        BigDecimal bankAmount = payment.amountNet().subtract(new BigDecimal("0.01"));

        ReconciliationResult result = reconciliationService.reconcile(
                "RET202504.TXT", PERIOD,
                List.of(line(cpf, payment.paymentId(), cents(bankAmount), "00")),
                PROCESS);

        assertThat(result.reconciled()).isEqualTo(1);
        assertThat(result.withinTolerance()).isEqualTo(1);
    }

    /**
     * {@code SIFAP-F5-01}. {@code BATCHCON.NSP:203} incrementa o contador antes do
     * {@code DECIDE}; o ramo {@code NONE} so escreve no log.
     */
    @Test
    @DisplayName("deve contar como pendente quando o codigo de retorno e desconhecido")
    void deve_contar_como_pendente_quando_o_codigo_de_retorno_e_desconhecido() {
        PaymentView payment = payrollAndRemittance(1).get(0);
        String cpf = unmasked(payment);

        ReconciliationResult result = reconciliationService.reconcile(
                "RET202504.TXT", PERIOD,
                List.of(line(cpf, payment.paymentId(), cents(payment.amountNet()), "77")),
                PROCESS);

        assertThat(result.reconciled()).isZero();
        assertThat(result.pending()).isEqualTo(1);
        assertThat(result.issues()).anyMatch(issue -> issue.type().equals("CODIGO_DESCONHECIDO"));
    }

    @Test
    @DisplayName("deve registrar pendencia consultavel quando nao ha pagamento correspondente")
    void deve_registrar_pendencia_consultavel_quando_nao_ha_pagamento_correspondente() {
        payrollAndRemittance(1);

        ReconciliationResult result = reconciliationService.reconcile(
                "RET202504.TXT", PERIOD,
                List.of(line(CpfGenerator.next(), "999999", "100000", "00")),
                PROCESS);

        assertThat(result.pending()).isEqualTo(1);
        assertThat(result.issues()).anyMatch(issue -> issue.type().equals("SEM_CORRESPONDENCIA"));
        assertThat(result.issues().get(0).maskedCpf()).doesNotContain("*****");
    }

    /** {@code REQ-REC-003}: a identidade e o resumo do conteudo, nao o nome. */
    @Test
    @DisplayName("deve recusar o mesmo conteudo quando submetido com outro nome")
    void deve_recusar_o_mesmo_conteudo_quando_submetido_com_outro_nome() {
        PaymentView payment = payrollAndRemittance(1).get(0);
        List<String> lines = List.of(line(unmasked(payment), payment.paymentId(),
                cents(payment.amountNet()), "00"));

        reconciliationService.reconcile("RET202504.TXT", PERIOD, lines, PROCESS);

        assertThatThrownBy(() ->
                        reconciliationService.reconcile("OUTRO_NOME.TXT", PERIOD, lines, PROCESS))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("ja processado");
    }

    /** {@code REQ-REC-011}: o legado grava a nova situacao sem consultar a anterior. */
    @Test
    @DisplayName("deve recusar o retorno quando o pagamento nao foi remetido ao banco")
    void deve_recusar_o_retorno_quando_o_pagamento_nao_foi_remetido_ao_banco() {
        registerBeneficiary();
        PayrollCycleResult cycle = cycleService.run(programCode, PERIOD, PROCESS);
        assertThat(cycle.generated()).isEqualTo(1);

        PaymentView payment = paymentQuery.findByCpf(Cpf.of(unmaskedFromCycle()), OPERATOR).get(0);

        ReconciliationResult result = reconciliationService.reconcile(
                "RET202504.TXT", PERIOD,
                List.of(line(unmasked(payment), payment.paymentId(), cents(payment.amountNet()), "00")),
                PROCESS);

        assertThat(result.pending()).isEqualTo(1);
        assertThat(result.issues()).anyMatch(issue -> issue.type().equals("TRANSICAO_INVALIDA"));
    }

    @Test
    @DisplayName("deve registrar cada conciliacao na trilha quando o ciclo termina")
    void deve_registrar_cada_conciliacao_na_trilha_quando_o_ciclo_termina() {
        List<PaymentView> payments = payrollAndRemittance(2);
        List<String> lines = payments.stream()
                .map(payment -> line(unmasked(payment), payment.paymentId(),
                        cents(payment.amountNet()), "00"))
                .toList();

        ReconciliationResult result = reconciliationService.reconcile(
                "RET202504.TXT", PERIOD, lines, PROCESS);

        List<AuditEventView> trail = auditQuery.findChangesByBatchRun(result.runId());
        assertThat(trail).hasSize(3);
        assertThat(trail).anyMatch(event -> event.entityType().equals("RECONCILIATION_FILE"));
    }

    @Test
    @DisplayName("deve recusar o banco fora do dominio quando o retorno traz codigo desconhecido")
    void deve_recusar_o_banco_fora_do_dominio_quando_o_retorno_traz_codigo_desconhecido() {
        PaymentView payment = payrollAndRemittance(1).get(0);
        String raw = line(unmasked(payment), payment.paymentId(), cents(payment.amountNet()), "00");
        char[] buffer = raw.toCharArray();
        put(buffer, 1, "999");

        ReconciliationResult result = reconciliationService.reconcile(
                "RET202504.TXT", PERIOD, List.of(new String(buffer)), PROCESS);

        assertThat(result.issues()).anyMatch(issue -> issue.type().equals("BANCO_DESCONHECIDO"));
    }

    /** A projecao mascara o CPF; o teste precisa do valor cru para montar o arquivo. */
    private String unmasked(PaymentView view) {
        return jdbcTemplate.queryForObject(
                "SELECT cpf FROM payment WHERE id = ?", String.class, Long.valueOf(view.paymentId()));
    }

    private String unmaskedFromCycle() {
        return jdbcTemplate.queryForObject(
                "SELECT cpf FROM payment WHERE reference_period = ? ORDER BY id DESC LIMIT 1",
                String.class, PERIOD);
    }
}
