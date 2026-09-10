package br.gov.sifap.socialprogram.internal;

import br.gov.sifap.socialprogram.SocialProgramParameters;
import br.gov.sifap.socialprogram.SocialProgramQuery;
import br.gov.sifap.socialprogram.SocialProgramStatus;
import br.gov.sifap.socialprogram.SocialProgramView;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura do catalogo.
 *
 * <p>Somente leitura de verdade, ao contrario da consulta de beneficiario: aqui nao ha
 * evento de acesso a publicar. Sao 45 registros de parametrizacao, sem dado pessoal.
 */
@Service
@Transactional(readOnly = true)
class SocialProgramQueryImpl implements SocialProgramQuery {

    private final SocialProgramRepository repository;

    SocialProgramQueryImpl(SocialProgramRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SocialProgramParameters> parametersOf(String programCode) {
        return repository.findByCode(programCode).map(SocialProgramMapper::toParameters);
    }

    @Override
    public Optional<SocialProgramView> findByCode(String programCode) {
        return repository.findByCode(programCode).map(SocialProgramMapper::toView);
    }

    @Override
    public List<SocialProgramView> findByStatus(SocialProgramStatus status) {
        return repository.findByStatusOrderByCodeAsc(status).stream()
                .map(SocialProgramMapper::toView)
                .toList();
    }
}
