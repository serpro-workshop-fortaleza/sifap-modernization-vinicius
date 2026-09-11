package br.gov.sifap.payment;

import br.gov.sifap.shared.document.Cpf;
import br.gov.sifap.shared.event.Actor;
import java.util.List;
import java.util.Optional;

/**
 * Interface publica de leitura da folha.
 *
 * <p>O {@link Actor} e obrigatorio pelo mesmo motivo do cadastro: consultar pagamento e
 * acessar dado pessoal, e o {@code REQ-AUD-004} exige registro.
 */
public interface PaymentQuery {

    Optional<PaymentView> findByCpfAndPeriod(Cpf cpf, String referencePeriod, Actor actor);

    List<PaymentView> findByCpf(Cpf cpf, Actor actor);
}
