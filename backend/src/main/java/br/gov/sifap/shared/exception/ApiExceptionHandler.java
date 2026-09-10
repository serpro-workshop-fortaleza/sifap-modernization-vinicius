package br.gov.sifap.shared.exception;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traducao de erro de dominio para resposta HTTP.
 *
 * <p>A resposta nunca carrega dado pessoal: apenas o REQ-ID e a descricao da regra.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorResponse(String requirementId, String message, Instant timestamp) {

        static ErrorResponse of(DomainRuleException exception) {
            return new ErrorResponse(
                    exception.requirementId(), exception.getMessage(), Instant.now());
        }
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> onNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(exception));
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ErrorResponse> onConflict(ResourceConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(exception));
    }

    @ExceptionHandler(DomainRuleException.class)
    public ResponseEntity<ErrorResponse> onRuleViolation(DomainRuleException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(exception));
    }
}
