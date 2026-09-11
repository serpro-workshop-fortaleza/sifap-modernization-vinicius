package br.gov.sifap.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.sifap.audit.AuditEventView;
import br.gov.sifap.audit.AuditQuery;
import br.gov.sifap.beneficiary.AddressData;
import br.gov.sifap.beneficiary.RegisterBeneficiaryCommand;
import br.gov.sifap.beneficiary.Sex;
import br.gov.sifap.beneficiary.internal.BeneficiaryTestFacade;
import br.gov.sifap.payment.BankRemittance;
import br.gov.sifap.payment.DiscountEntry;
import br.gov.sifap.payment.DiscountType;
import br.gov.sifap.payment.PaymentQuery;
import br.gov.sifap.payment.PaymentStatus;
import br.gov.sifap.payment.PaymentView;
import br.gov.sifap.payment.PayrollCycleResult;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("Ciclo de folha")
class PayrollCycleIT extends AbstractIntegrationTest {

    private static final Actor OPERATOR = Actor.human("op-folha", "SUPERVISOR");
    private static final Actor PROCESS = Actor.process("BATCHPGT");
    private static final AtomicInteger PROGRAM_SEQUENCE = new AtomicInteger(1000);
    private static final String PERIOD = "202503";

    @Autowired
    private PayrollCycleService cycleService;

    @Autowired
    private PaymentAdjustmentService adjustmentService;

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
    void registerProgram() {
        // A aplicacao nao cria particao; em producao isso e job de operacao.
        new PaymentPartitionMaintenance(jdbcTemplate, Clock.systemUTC()).ensurePartition(PERIOD);

        programCode = String.valueOf(PROGRAM_SEQUENCE.incrementAndGet());
        programs.register(
                new RegisterSocialProgramCommand(
                        programCode,
                        "Programa de folha",
                        "PFL",
                        SocialProgramType.ASSISTENCIA,
                        new BigDecimal("1000.00"),
                        BigDecimal.ZERO,
                        null,
                        0,
                        0,
                        null,
                        "LEI 1/2025",
                        LocalDate.of(2025, 1, 1)),
                OPERATOR);
        programs.replaceRegions(
                programCode,
                new ReplaceRegionalParametersCommand(List.of(
                        new RegionData("01", new BigDecimal("1.0000"), BigDecimal.ZERO, true),
                        new RegionData("99", new BigDecimal("1.0000"), BigDecimal.ZERO, true))),
                OPERATOR);
        programs.replaceBands(
                programCode,
                new ReplaceCalculationBandsCommand(List.of(new BandData(
                        BigDecimal.ZERO, new BigDecimal("9999.99"), new BigDecimal("1.0000"), BigDecimal.ZERO, false))),
                OPERATOR);
    }

    private String registerBeneficiary(String income) {
        String cpf = CpfGenerator.next();
        beneficiaries.register(
                new RegisterBeneficiaryCommand(
                        cpf,
                        null,
                        "Beneficiario de teste",
                        LocalDate.of(1990, 5, 20),
                        Sex.F,
                        programCode,
                        new BigDecimal(income),
                        new AddressData("Rua A", "10", null, "Centro", "Brasilia", "DF", "70000000", "01"),
                        null,
                        null),
                OPERATOR);
        return cpf;
    }

    @Test
    @DisplayName("deve gerar um pagamento por beneficiario elegivel quando o ciclo e executado")
    void deve_gerar_um_pagamento_por_beneficiario_elegivel_quando_o_ciclo_e_executado() {
        registerBeneficiary("100.00");
        registerBeneficiary("200.00");

        PayrollCycleResult result = cycleService.run(programCode, PERIOD, PROCESS);

        assertThat(result.generated()).isEqualTo(2);
        assertThat(result.rejected()).isZero();
        assertThat(result.totalGross()).isEqualByComparingTo("2000.00");
    }

    /**
     * {@code REQ-PAY-003}. {@code BATCHPGT} nao verifica se o periodo ja foi processado;
     * reexecutar o job duplica a folha inteira ({@code SIFAP-M-06}).
     */
    @Test
    @DisplayName("deve recusar a segunda execucao quando o ciclo do periodo ja existe")
    void deve_recusar_a_segunda_execucao_quando_o_ciclo_do_periodo_ja_existe() {
        registerBeneficiary("100.00");
        cycleService.run(programCode, PERIOD, PROCESS);

        assertThatThrownBy(() -> cycleService.run(programCode, PERIOD, PROCESS))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("ja executado");
    }

    /**
     * {@code REQ-AUD-010}. Hoje a folha inteira produz um unico registro de auditoria.
     */
    @Test
    @DisplayName("deve registrar cada pagamento na trilha quando o ciclo termina")
    void deve_registrar_cada_pagamento_na_trilha_quando_o_ciclo_termina() {
        registerBeneficiary("100.00");
        registerBeneficiary("200.00");

        PayrollCycleResult result = cycleService.run(programCode, PERIOD, PROCESS);

        List<AuditEventView> trail = auditQuery.findChangesByBatchRun(result.cycleId());
        assertThat(trail).hasSize(3);
        assertThat(trail).anyMatch(event -> event.entityType().equals("PAYROLL_CYCLE"));
        assertThat(trail.stream().filter(event -> event.entityType().equals("PAYMENT")))
                .hasSize(2);
    }

    @Test
    @DisplayName("deve recalcular o liquido e registrar o evento quando os descontos sao lancados")
    void deve_recalcular_o_liquido_e_registrar_o_evento_quando_os_descontos_sao_lancados() {
        String cpf = registerBeneficiary("100.00");
        cycleService.run(programCode, PERIOD, PROCESS);

        adjustmentService.applyDiscounts(
                cpf,
                PERIOD,
                List.of(DiscountEntry.of(DiscountType.CONSIGNADO, new BigDecimal("100.00"))),
                OPERATOR);

        Optional<PaymentView> view = paymentQuery.findByCpfAndPeriod(Cpf.of(cpf), PERIOD, OPERATOR);
        assertThat(view).isPresent();
        assertThat(view.get().amountNet())
                .isEqualByComparingTo(view.get().amountGross().subtract(view.get().amountDiscount()));
        assertThat(view.get().discounts()).hasSize(1);
    }

    @Test
    @DisplayName("deve emitir a remessa uma unica vez quando o ciclo esta concluido")
    void deve_emitir_a_remessa_uma_unica_vez_quando_o_ciclo_esta_concluido() {
        registerBeneficiary("100.00");
        PayrollCycleResult result = cycleService.run(programCode, PERIOD, PROCESS);

        BankRemittance remittance = remittanceService.issue(result.cycleId(), PERIOD, PROCESS);

        assertThat(remittance.records()).isEqualTo(1);
        assertThat(remittance.controlTotal()).isEqualByComparingTo(result.totalNet());
        assertThatThrownBy(() -> remittanceService.issue(result.cycleId(), PERIOD, PROCESS))
                .isInstanceOf(DomainRuleException.class);
    }

    @Test
    @DisplayName("deve marcar o pagamento como emitido quando a remessa e gerada")
    void deve_marcar_o_pagamento_como_emitido_quando_a_remessa_e_gerada() {
        String cpf = registerBeneficiary("100.00");
        PayrollCycleResult result = cycleService.run(programCode, PERIOD, PROCESS);
        remittanceService.issue(result.cycleId(), PERIOD, PROCESS);

        assertThat(paymentQuery.findByCpfAndPeriod(Cpf.of(cpf), PERIOD, OPERATOR))
                .get()
                .extracting(PaymentView::status)
                .isEqualTo(PaymentStatus.EMITIDO);
    }

    @Test
    @DisplayName("deve mascarar o CPF quando o pagamento e consultado")
    void deve_mascarar_o_cpf_quando_o_pagamento_e_consultado() {
        String cpf = registerBeneficiary("100.00");
        cycleService.run(programCode, PERIOD, PROCESS);

        List<PaymentView> history = paymentQuery.findByCpf(Cpf.of(cpf), OPERATOR);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).maskedCpf()).doesNotContain(cpf);
    }

    @Test
    @DisplayName("deve guardar os fatores aplicados quando o pagamento e gerado")
    void deve_guardar_os_fatores_aplicados_quando_o_pagamento_e_gerado() {
        String cpf = registerBeneficiary("100.00");
        cycleService.run(programCode, PERIOD, PROCESS);

        PaymentView view = paymentQuery.findByCpfAndPeriod(Cpf.of(cpf), PERIOD, OPERATOR).orElseThrow();

        // AC-010.2: a divergencia com a RN-013 deixa de depender de leitura de Natural.
        assertThat(view.appliedFactors()).hasSize(5);
    }
}
