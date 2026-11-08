package com.stanley.ledger.api;

import com.stanley.ledger.ledger.LedgerException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(LedgerException.class)
  ResponseEntity<Map<String, String>> ledger(LedgerException e) {
    return ResponseEntity.status(e.httpStatus())
        .body(Map.of("code", e.code(), "message", e.getMessage()));
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<Map<String, String>> header(MissingRequestHeaderException e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of("code", "MISSING_HEADER", "message", e.getHeaderName() + " header is required"));
  }

  @ExceptionHandler(JacksonException.class)
  ResponseEntity<Map<String, String>> badJson(JacksonException e) {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "BAD_JSON", "message", "body is not valid JSON"));
  }
}
