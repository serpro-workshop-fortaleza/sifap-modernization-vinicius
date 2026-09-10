package br.gov.sifap.beneficiary;

import br.gov.sifap.shared.document.Cpf;
import br.gov.sifap.shared.document.Nis;
import br.gov.sifap.shared.event.Actor;
import java.util.Optional;

/**
 * Interface publica de leitura do cadastro.
 *
 * <p>Atende {@code REQ-BEN-015}. O {@link Actor} e obrigatorio porque o
 * {@code REQ-BEN-016} exige registrar quem acessou dado pessoal. Sem ele na assinatura, o
 * registro dependeria de o chamador lembrar — que e o padrao que a Fatia 1 eliminou da
 * auditoria ao trocar {@code PERFORM WRITE-AUDIT} por evento de dominio.
 */
public interface BeneficiaryQuery {

    Optional<BeneficiaryView> findByCpf(Cpf cpf, Actor actor);

    Optional<BeneficiaryView> findByNis(Nis nis, Actor actor);
}
