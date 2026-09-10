package br.gov.sifap.socialprogram;

import java.util.List;
import java.util.Optional;

/**
 * Interface publica de leitura do catalogo.
 *
 * <p>Duas projecoes, dois consumidores: {@link SocialProgramParameters} para o calculo da
 * Fatia 4, {@link SocialProgramView} para a administracao.
 *
 * <p>Consulta a programa social <strong>nao</strong> publica evento de acesso. Sao 45
 * registros de parametrizacao, sem dado pessoal; auditar essa leitura repetiria o problema
 * de volume que motivou a {@code PORT. CGTI 213/2010}.
 */
public interface SocialProgramQuery {

    Optional<SocialProgramParameters> parametersOf(String programCode);

    Optional<SocialProgramView> findByCode(String programCode);

    List<SocialProgramView> findByStatus(SocialProgramStatus status);
}
