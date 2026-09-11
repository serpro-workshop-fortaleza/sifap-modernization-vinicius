package br.gov.sifap.beneficiary.internal;

import br.gov.sifap.beneficiary.BeneficiaryPayrollFeed;
import br.gov.sifap.beneficiary.PayrollCandidate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura paginada do cadastro para a folha.
 *
 * <p>Somente leitura de verdade: diferente de {@code BeneficiaryQueryImpl}, que precisa
 * escrever o evento de acesso, aqui nao ha registro por beneficiario, e a transacao pode
 * ser {@code readOnly}.
 */
@Service
class BeneficiaryPayrollFeedImpl implements BeneficiaryPayrollFeed {

    private final BeneficiaryPayrollRepository repository;
    private final Clock clock;

    BeneficiaryPayrollFeedImpl(BeneficiaryPayrollRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollCandidate> nextPage(String programCode, String afterCpf, int limit) {
        LocalDate today = LocalDate.now(clock);
        String cursor = afterCpf == null ? "" : afterCpf;

        return repository.findPayrollPage(programCode, cursor, Limit.of(limit)).stream()
                .map(row -> toCandidate(row, today))
                .toList();
    }

    private static PayrollCandidate toCandidate(PayrollRow row, LocalDate today) {
        return new PayrollCandidate(
                row.cpf(),
                row.programCode(),
                Optional.ofNullable(row.status()),
                Optional.ofNullable(row.familyIncome()),
                row.regionCode() == null ? "" : row.regionCode(),
                Math.toIntExact(row.activeDependents()),
                ageOf(row.birthDate(), today));
    }

    private static Optional<Integer> ageOf(LocalDate birthDate, LocalDate today) {
        if (birthDate == null || birthDate.isAfter(today)) {
            return Optional.empty();
        }
        return Optional.of(Period.between(birthDate, today).getYears());
    }
}
