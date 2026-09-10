package br.gov.sifap.socialprogram.internal;

import br.gov.sifap.shared.event.Actor;
import br.gov.sifap.socialprogram.ChangeSocialProgramStatusCommand;
import br.gov.sifap.socialprogram.RegisterSocialProgramCommand;
import br.gov.sifap.socialprogram.ReplaceCalculationBandsCommand;
import br.gov.sifap.socialprogram.ReplaceRegionalParametersCommand;
import br.gov.sifap.socialprogram.SocialProgramView;
import br.gov.sifap.socialprogram.UpdateSocialProgramCommand;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Da acesso ao servico de escrita a partir dos testes de integracao. */
@Component
public class SocialProgramTestFacade {

    private final SocialProgramService service;

    SocialProgramTestFacade(SocialProgramService service) {
        this.service = service;
    }

    public SocialProgramView register(RegisterSocialProgramCommand command, Actor actor) {
        return service.register(command, actor);
    }

    public SocialProgramView update(String code, UpdateSocialProgramCommand command, Actor actor) {
        return service.update(code, command, actor);
    }

    public SocialProgramView changeStatus(
            String code, ChangeSocialProgramStatusCommand command, Actor actor) {
        return service.changeStatus(code, command, actor);
    }

    public SocialProgramView replaceBands(
            String code, ReplaceCalculationBandsCommand command, Actor actor) {
        return service.replaceBands(code, command, actor);
    }

    public SocialProgramView replaceRegions(
            String code, ReplaceRegionalParametersCommand command, Actor actor) {
        return service.replaceRegions(code, command, actor);
    }

    public BigDecimal derivedFactorOf(String code) {
        return service.derivedFactorOf(code);
    }

    @Transactional
    public void registerThenFail(RegisterSocialProgramCommand command, Actor actor) {
        service.register(command, actor);
        throw new IllegalStateException("falha deliberada apos gravar o programa");
    }
}
