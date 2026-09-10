package br.gov.sifap.beneficiary.internal;

import br.gov.sifap.beneficiary.BeneficiaryQuery;
import br.gov.sifap.beneficiary.BeneficiaryView;
import br.gov.sifap.beneficiary.event.BeneficiaryQueried;
import br.gov.sifap.shared.document.Cpf;
import br.gov.sifap.shared.document.Nis;
import br.gov.sifap.shared.event.Actor;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura do cadastro.
 *
 * <p>A transacao <strong>nao</strong> e somente leitura, e isso e consequencia do
 * {@code REQ-BEN-016}: consultar dado pessoal grava um evento de acesso. O legado faz o
 * mesmo, com {@code PERFORM WRITE-AUDIT} seguido de {@code END TRANSACTION} em
 * {@code CONSBENF.NSP:178-179}.
 *
 * <p>E este custo — toda leitura vira escrita — que motivou a {@code PORT. CGTI 213/2010}
 * a proibir a auditoria de consultas por volume. A resposta aqui nao e deixar de
 * registrar, e sim isolar o registro em tabela propria, com retencao propria.
 */
@Service
@Transactional
class BeneficiaryQueryImpl implements BeneficiaryQuery {

    private final BeneficiaryRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    BeneficiaryQueryImpl(
            BeneficiaryRepository repository, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public Optional<BeneficiaryView> findByCpf(Cpf cpf, Actor actor) {
        return present(repository.findByCpf(cpf.value()), actor, "consulta por CPF");
    }

    @Override
    public Optional<BeneficiaryView> findByNis(Nis nis, Actor actor) {
        return present(repository.findByNis(nis.value()), actor, "consulta por NIS");
    }

    private Optional<BeneficiaryView> present(
            Optional<Beneficiary> found, Actor actor, String purpose) {
        // REQ-BEN-016: o legado so registra o acesso quando encontra o registro
        // (CONSBENF.NSP:150-179 devolve REINPUT antes da trilha quando nao ha registro).
        found.ifPresent(beneficiary -> events.publishEvent(
                new BeneficiaryQueried(beneficiary.cpf(), purpose, actor, clock.instant())));

        return found.map(beneficiary ->
                BeneficiaryViewMapper.toView(beneficiary, LocalDate.now(clock)));
    }
}
