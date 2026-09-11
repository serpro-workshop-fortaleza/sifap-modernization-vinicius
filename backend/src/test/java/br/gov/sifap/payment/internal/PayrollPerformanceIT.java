package br.gov.sifap.payment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.gov.sifap.beneficiary.AddressData;
import br.gov.sifap.beneficiary.RegisterBeneficiaryCommand;
import br.gov.sifap.beneficiary.Sex;
import br.gov.sifap.beneficiary.internal.BeneficiaryTestFacade;
import br.gov.sifap.payment.PayrollCycleResult;
import br.gov.sifap.shared.event.Actor;
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
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-414 — apoia {@code REQ-PAY-022}.
 *
 * <p>O legado processa 3,8 milhoes de pagamentos em uma janela que, segundo o cabecalho de
 * {@code BATCHPGT.NSP:39}, ja passa de quatro horas. Isso da cerca de 264 pagamentos por
 * segundo apenas para nao piorar.
 *
 * <p>Mede-se uma folha reduzida e extrapola-se. Descobrir que o desenho nao aguenta o
 * volume depois de pronto e o modo caro de descobrir.
 */
@DisplayName("Desempenho do ciclo de folha")
class PayrollPerformanceIT extends AbstractIntegrationTest {

    private static final int BENEFICIARIES = 3_000;
    private static final String PERIOD = "202411";
    private static final int LEGACY_THROUGHPUT_PER_SECOND = 264;

    private static final Actor OPERATOR = Actor.human("op-carga", "SUPERVISOR");
    private static final Actor PROCESS = Actor.process("BATCHPGT");

    @Autowired
    private PayrollCycleService cycleService;

    @Autowired
    private BeneficiaryTestFacade beneficiaries;

    @Autowired
    private SocialProgramTestFacade programs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("deve superar a vazao do legado quando o ciclo processa milhares de beneficiarios")
    void deve_superar_a_vazao_do_legado_quando_o_ciclo_processa_milhares_de_beneficiarios() {
        new PaymentPartitionMaintenance(jdbcTemplate, Clock.systemUTC()).ensurePartition(PERIOD);
        String programCode = "9001";
        registerProgram(programCode);

        for (int index = 0; index < BENEFICIARIES; index++) {
            registerBeneficiary(programCode);
        }

        long startedAt = System.nanoTime();
        PayrollCycleResult result = cycleService.run(programCode, PERIOD, PROCESS);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        long perSecond = BENEFICIARIES * 1000L / Math.max(1, elapsed.toMillis());

        assertThat(result.generated()).isEqualTo(BENEFICIARIES);
        assertThat(perSecond)
                .as("vazao de %d pagamentos por segundo contra os %d do legado", perSecond,
                        LEGACY_THROUGHPUT_PER_SECOND)
                .isGreaterThan(LEGACY_THROUGHPUT_PER_SECOND);
    }

    private void registerProgram(String programCode) {
        programs.register(
                new RegisterSocialProgramCommand(
                        programCode,
                        "Programa de carga",
                        "PCG",
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
                new ReplaceRegionalParametersCommand(
                        List.of(new RegionData("01", new BigDecimal("1.0000"), BigDecimal.ZERO, true))),
                OPERATOR);
        programs.replaceBands(
                programCode,
                new ReplaceCalculationBandsCommand(List.of(new BandData(
                        BigDecimal.ZERO, new BigDecimal("9999.99"), new BigDecimal("1.0000"), BigDecimal.ZERO, false))),
                OPERATOR);
    }

    private void registerBeneficiary(String programCode) {
        beneficiaries.register(
                new RegisterBeneficiaryCommand(
                        CpfGenerator.next(),
                        null,
                        "Beneficiario de carga",
                        LocalDate.of(1985, 3, 12),
                        Sex.M,
                        programCode,
                        new BigDecimal("500.00"),
                        new AddressData("Rua B", "20", null, "Centro", "Brasilia", "DF", "70000000", "01"),
                        null,
                        null),
                OPERATOR);
    }
}
