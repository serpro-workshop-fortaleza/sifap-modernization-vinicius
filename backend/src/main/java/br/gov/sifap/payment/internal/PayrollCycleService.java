package br.gov.sifap.payment.internal;

import br.gov.sifap.beneficiary.BeneficiaryPayrollFeed;
import br.gov.sifap.beneficiary.PayrollCandidate;
import br.gov.sifap.payment.PayrollCycleResult;
import br.gov.sifap.payment.event.PayrollCycleCompleted;
import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.shared.exception.DomainRuleException;
import br.gov.sifap.shared.exception.ResourceNotFoundException;
import br.gov.sifap.socialprogram.SocialProgramQuery;
import br.gov.sifap.socialprogram.SocialProgramView;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Execucao de um ciclo de folha.
 *
 * <p>Atende {@code REQ-PAY-001} a {@code REQ-PAY-003}, {@code REQ-PAY-022} e
 * {@code REQ-PAY-023}.
 *
 * <p>Le em blocos e grava bloco a bloco. O {@code BATCHPGT.NSP:189-500} mantem um
 * {@code READ LOGICAL} aberto sobre 3,8 milhoes de registros com {@code END TRANSACTION}
 * dentro do laco: a falha no registro dois milhoes deixa a folha pela metade sem marcar
 * onde parou.
 *
 * <p>Nao ha {@code @Transactional} aqui de proposito. Uma transacao unica sobre a folha
 * inteira nao sobreviveria a janela de quatro horas.
 */
@Service
public class PayrollCycleService {

    /** Tamanho do bloco; {@code BATCHPGT.NSP:512} confirma a cada 1000 sem controle proprio. */
    static final int BLOCK_SIZE = 5000;

    private final BeneficiaryPayrollFeed feed;
    private final SocialProgramQuery programQuery;
    private final PaymentRepository payments;
    private final PayrollBlockProcessor blockProcessor;
    private final PaymentPartitionGuard partitionGuard;
    private final PayrollEventRecorder recorder;
    private final Clock clock;

    PayrollCycleService(
            BeneficiaryPayrollFeed feed,
            SocialProgramQuery programQuery,
            PaymentRepository payments,
            PayrollBlockProcessor blockProcessor,
            PaymentPartitionGuard partitionGuard,
            PayrollEventRecorder recorder,
            Clock clock) {
        this.feed = feed;
        this.programQuery = programQuery;
        this.payments = payments;
        this.blockProcessor = blockProcessor;
        this.partitionGuard = partitionGuard;
        this.recorder = recorder;
        this.clock = clock;
    }

    public PayrollCycleResult run(String programCode, String referencePeriod, Actor actor) {
        SocialProgramView program = programQuery
                .findByCode(programCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REQ-PAY-001", "programa social nao encontrado: " + programCode));

        String cycleId = cycleIdOf(programCode, referencePeriod);
        if (payments.countByCycleId(cycleId) > 0) {
            throw new DomainRuleException(
                    "REQ-PAY-003", "ciclo ja executado para o periodo " + referencePeriod);
        }
        partitionGuard.requirePartition(referencePeriod);

        CycleTally tally = new CycleTally();
        String cursor = null;

        while (true) {
            List<PayrollCandidate> block = feed.nextPage(programCode, cursor, BLOCK_SIZE);
            if (block.isEmpty()) {
                break;
            }
            blockProcessor.process(block, program, referencePeriod, cycleId, actor, tally);
            cursor = block.get(block.size() - 1).cpf();
        }

        recorder.record(new PayrollCycleCompleted(
                cycleId,
                referencePeriod,
                tally.generated(),
                tally.alreadyPaid(),
                tally.rejected(),
                tally.totalGross(),
                tally.totalNet(),
                actor,
                clock.instant()));

        return new PayrollCycleResult(
                cycleId,
                referencePeriod,
                tally.generated(),
                tally.alreadyPaid(),
                tally.rejected(),
                tally.totalGross(),
                tally.totalNet(),
                tally.rejections());
    }

    private static String cycleIdOf(String programCode, String referencePeriod) {
        return programCode + "-" + referencePeriod;
    }
}
