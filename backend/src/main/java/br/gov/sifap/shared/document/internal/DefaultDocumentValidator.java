package br.gov.sifap.shared.document.internal;

import br.gov.sifap.shared.document.DocumentValidator;
import br.gov.sifap.shared.document.ValidationResult;
import org.springframework.stereotype.Component;

/**
 * Fachada injetavel do kernel de validacao de documentos.
 *
 * <p>A regra de negocio permanece nas funcoes puras {@link CpfValidatorImpl} e
 * {@link NisValidatorImpl}; este bean existe apenas para que os modulos de dominio
 * dependam da interface publica, e nao do pacote {@code internal}.
 */
@Component
public class DefaultDocumentValidator implements DocumentValidator {

    @Override
    public ValidationResult validateCpf(String cpf) {
        return CpfValidatorImpl.validate(cpf);
    }

    @Override
    public ValidationResult validateNis(String nis) {
        return NisValidatorImpl.validate(nis);
    }
}
