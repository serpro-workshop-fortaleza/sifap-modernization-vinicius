package br.gov.sifap.shared.exception;

/** Recurso ja existe ou conflita com o estado atual. */
public class ResourceConflictException extends DomainRuleException {

    public ResourceConflictException(String requirementId, String message) {
        super(requirementId, message);
    }
}
