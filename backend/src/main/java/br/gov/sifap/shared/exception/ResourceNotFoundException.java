package br.gov.sifap.shared.exception;

/** Recurso nao encontrado. */
public class ResourceNotFoundException extends DomainRuleException {

    public ResourceNotFoundException(String requirementId, String message) {
        super(requirementId, message);
    }
}
