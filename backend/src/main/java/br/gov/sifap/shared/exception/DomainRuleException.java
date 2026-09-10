package br.gov.sifap.shared.exception;

/**
 * Violacao de regra de negocio.
 *
 * <p>Carrega o REQ-ID que a regra atende. A rastreabilidade deixa de existir apenas no
 * commit e passa a acompanhar o erro devolvido a quem chamou, o que torna verificavel
 * em teste que a recusa veio da regra pretendida.
 */
public class DomainRuleException extends RuntimeException {

    private final String requirementId;

    public DomainRuleException(String requirementId, String message) {
        super(message);
        this.requirementId = requirementId;
    }

    public String requirementId() {
        return requirementId;
    }
}
