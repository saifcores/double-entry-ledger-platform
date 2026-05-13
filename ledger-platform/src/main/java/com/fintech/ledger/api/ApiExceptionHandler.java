package com.fintech.ledger.api;

import java.net.URI;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase());
    pd.setTitle(status.getReasonPhrase());
    if (ex.getCause() != null) {
      pd.setProperty("cause", ex.getCause().getClass().getSimpleName());
    }
    return ResponseEntity.status(status).body(pd);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
    String msg =
        ex.getBindingResult().getFieldErrors().stream()
            .map(ApiExceptionHandler::formatFieldError)
            .collect(Collectors.joining("; "));
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg.isEmpty() ? "Validation failed" : msg);
    pd.setTitle("Bad Request");
    pd.setType(URI.create("about:blank"));
    return ResponseEntity.badRequest().body(pd);
  }

  private static String formatFieldError(FieldError e) {
    return e.getField() + ": " + e.getDefaultMessage();
  }
}
