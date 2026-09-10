package br.gov.sifap.shared.document;

/**
 * Interface publica do kernel de validacao de documentos.
 *
 * <p>Substitui as cinco implementacoes divergentes do legado
 * ({@code CCVALCPF}, {@code SUBVALCP}, {@code SUBVALNI} e as copias embutidas
 * em {@code CADBENEF} e {@code VALBENEF}), conforme o ADR-0005.
 *
 * <p>Toda implementacao e pura: sem estado, sem persistencia e sem efeito colateral
 * ({@code REQ-DOC-007}).
 */
public interface DocumentValidator {

    ValidationResult validateCpf(String cpf);

    ValidationResult validateNis(String nis);
}
