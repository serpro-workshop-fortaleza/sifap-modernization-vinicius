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

    /**
     * Pagamentos com divergencia entre o valor pago e o confirmado pelo banco.
     *
     * <p>Atende {@code AC-009.2}. E a consulta que o legado nao tem: a divergencia de
     * {@code BATCHCON.NSP:192-201} so existe na trilha, e lista-la exige varrer 418
     * milhoes de eventos.
     */
    List<PaymentView> findDivergent(String referencePeriod, Actor actor);
}
