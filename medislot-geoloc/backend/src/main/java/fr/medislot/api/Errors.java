package fr.medislot.api;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class Errors {
  private static final Logger LOG = LoggerFactory.getLogger(Errors.class);

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ProblemDetail> status(ResponseStatusException e) {
    var p =
        ProblemDetail.forStatusAndDetail(
            e.getStatusCode(), e.getReason() == null ? "Demande refusée" : e.getReason());
    return ResponseEntity.status(e.getStatusCode()).body(p);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
    var p =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Vérifiez les données saisies.");
    p.setProperty(
        "errors",
        e.getBindingResult().getFieldErrors().stream()
            .map(
                f ->
                    java.util.Map.of(
                        "field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
            .toList());
    return ResponseEntity.badRequest().body(p);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> invalidParameter(MethodArgumentTypeMismatchException e) {
    return ResponseEntity.badRequest()
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Paramètre invalide : " + e.getName() + "."));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> conflict() {
    return ResponseEntity.status(409)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "La demande entre en conflit avec les données actuelles."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> unexpected(Exception e) {
    Span span = Span.current();
    String traceId = span.getSpanContext().getTraceId();
    span.recordException(e);
    span.setStatus(StatusCode.ERROR);
    LOG.error("Erreur inattendue, référence {} : {}", traceId, e.getMessage(), e);
    var p =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Le service est momentanément indisponible. Référence : " + traceId);
    p.setProperty("reference", traceId);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(p);
  }
}
