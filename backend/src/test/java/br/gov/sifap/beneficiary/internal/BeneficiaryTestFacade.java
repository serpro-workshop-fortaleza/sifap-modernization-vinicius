package br.gov.sifap.beneficiary.internal;

import br.gov.sifap.beneficiary.AddDependentCommand;
import br.gov.sifap.beneficiary.BeneficiaryView;
import br.gov.sifap.beneficiary.ChangeBeneficiaryStatusCommand;
import br.gov.sifap.beneficiary.RegisterBeneficiaryCommand;
import br.gov.sifap.beneficiary.UpdateBeneficiaryCommand;
import br.gov.sifap.shared.event.Actor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Da acesso ao servico de escrita a partir dos testes de integracao.
 *
 * <p>Existe para que {@code BeneficiaryService} permaneca package-private em producao: a
 * escrita e alcancada pela API REST, e nao por outro modulo.
 */
@Component
public class BeneficiaryTestFacade {

    private final BeneficiaryService service;

    BeneficiaryTestFacade(BeneficiaryService service) {
        this.service = service;
    }

    public BeneficiaryView register(RegisterBeneficiaryCommand command, Actor actor) {
        return service.register(command, actor);
    }

    public BeneficiaryView update(String cpf, UpdateBeneficiaryCommand command, Actor actor) {
        return service.update(cpf, command, actor);
    }

    public BeneficiaryView changeStatus(
            String cpf, ChangeBeneficiaryStatusCommand command, Actor actor) {
        return service.changeStatus(cpf, command, actor);
    }

    public BeneficiaryView addDependent(String cpf, AddDependentCommand command, Actor actor) {
        return service.addDependent(cpf, command, actor);
    }

    /** Simula a transacao de negocio que falha depois de gravar e publicar o evento. */
    @Transactional
    public void registerThenFail(RegisterBeneficiaryCommand command, Actor actor) {
        service.register(command, actor);
        throw new IllegalStateException("falha deliberada apos gravar o beneficiario");
    }
}
