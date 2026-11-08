package com.stanley.ledger.api;

import com.stanley.ledger.idem.IdempotentExecutor;
import com.stanley.ledger.idem.IdempotentExecutor.Stored;
import com.stanley.ledger.ledger.AccountState;
import com.stanley.ledger.ledger.LedgerException;
import com.stanley.ledger.ledger.LedgerService;
import com.stanley.ledger.recon.ReconciliationService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class LedgerController {
  private final LedgerService ledger;
  private final IdempotentExecutor idem;
  private final ReconciliationService recon;
  private final JdbcClient jdbc;
  private final JsonMapper json;

  public LedgerController(
      LedgerService ledger,
      IdempotentExecutor idem,
      ReconciliationService recon,
      JdbcClient jdbc,
      JsonMapper json) {
    this.ledger = ledger;
    this.idem = idem;
    this.recon = recon;
    this.jdbc = jdbc;
    this.json = json;
  }

  @PostMapping("/accounts")
  public ResponseEntity<String> createAccount(
      HttpServletRequest req,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody String body) {
    return write(
        req,
        key,
        body,
        201,
        n -> {
          long floor = n.has("floorMinor") ? n.get("floorMinor").asLong() : 0;
          return account(ledger.createAccount(text(n, "name"), text(n, "currency"), floor));
        });
  }

  @GetMapping("/accounts/{id}/balance")
  public Object balance(@PathVariable UUID id) {
    return account(ledger.balance(id));
  }

  /** Read model built by the Kafka consumer. May lag the ledger for a moment. */
  @GetMapping("/accounts/{id}/balance/projection")
  public Object projection(@PathVariable UUID id) {
    return jdbc.sql(
            "SELECT posted_minor, held_minor, event_count FROM balance_projection"
                + " WHERE account_id = :i")
        .param("i", id)
        .query((rs, n) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("accountId", id);
          m.put("postedMinor", rs.getLong("posted_minor"));
          m.put("heldMinor", rs.getLong("held_minor"));
          m.put("eventCount", rs.getLong("event_count"));
          return m;
        })
        .optional()
        .orElseThrow(() -> LedgerException.notFound("no projection for " + id));
  }

  @PostMapping("/transfers")
  public ResponseEntity<String> transfer(
      HttpServletRequest req,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody String body) {
    return write(
        req,
        key,
        body,
        201,
        n -> {
          UUID txn =
              ledger.transfer(
                  uuid(n, "fromAccountId"), uuid(n, "toAccountId"), amount(n, "amountMinor"));
          return Map.of("transactionId", txn);
        });
  }

  @PostMapping("/payments")
  public ResponseEntity<String> authorize(
      HttpServletRequest req,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody String body) {
    return write(
        req,
        key,
        body,
        201,
        n ->
            ledger.authorize(
                uuid(n, "payerAccountId"), uuid(n, "payeeAccountId"), amount(n, "amountMinor")));
  }

  @PostMapping("/payments/{id}/capture")
  public ResponseEntity<String> capture(
      HttpServletRequest req,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody(required = false) String body) {
    return write(
        req,
        key,
        body == null ? "" : body,
        200,
        n -> ledger.capture(id, n.has("amountMinor") ? amount(n, "amountMinor") : null));
  }

  @PostMapping("/payments/{id}/void")
  public ResponseEntity<String> voidPayment(
      HttpServletRequest req,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody(required = false) String body) {
    return write(req, key, body == null ? "" : body, 200, n -> ledger.voidPayment(id));
  }

  @PostMapping("/payments/{id}/refund")
  public ResponseEntity<String> refund(
      HttpServletRequest req,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody String body) {
    return write(req, key, body, 200, n -> ledger.refund(id, amount(n, "amountMinor")));
  }

  @GetMapping("/payments/{id}")
  public Object payment(@PathVariable UUID id) {
    return ledger.payment(id);
  }

  @PostMapping(
      path = "/reconciliations",
      consumes = {"text/csv", "text/plain"})
  public ResponseEntity<String> reconcile(
      HttpServletRequest req,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody String csv) {
    String hash = IdempotentExecutor.hash("POST", req.getRequestURI(), csv);
    Stored s =
        idem.run(
            (String) req.getAttribute(ApiKeyFilter.CLIENT_ATTR),
            key,
            hash,
            () ->
                new Stored(
                    201, json.writeValueAsString(recon.reconcile(new StringReader(csv))), false));
    return respond(s);
  }

  @GetMapping("/reconciliations/latest")
  public Object latestReport() {
    return recon.latest();
  }

  @GetMapping("/reconciliations/{id}")
  public Object report(@PathVariable UUID id) {
    return recon.get(id);
  }

  private ResponseEntity<String> write(
      HttpServletRequest req,
      String key,
      String body,
      int okStatus,
      Function<JsonNode, Object> action) {
    if (key.isBlank()) {
      throw LedgerException.invalid("Idempotency-Key must not be blank");
    }
    String hash = IdempotentExecutor.hash(req.getMethod(), req.getRequestURI(), body);
    Stored s =
        idem.run(
            (String) req.getAttribute(ApiKeyFilter.CLIENT_ATTR),
            key,
            hash,
            () -> {
              JsonNode n = body.isBlank() ? json.createObjectNode() : json.readTree(body);
              return new Stored(okStatus, json.writeValueAsString(action.apply(n)), false);
            });
    return respond(s);
  }

  private static ResponseEntity<String> respond(Stored s) {
    var b = ResponseEntity.status(s.status()).contentType(MediaType.APPLICATION_JSON);
    if (s.replay()) {
      b = b.header("Idempotent-Replay", "true");
    }
    return b.body(s.body());
  }

  private static Map<String, Object> account(AccountState a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("accountId", a.id());
    m.put("currency", a.currency());
    m.put("floorMinor", a.floorMinor());
    m.put("postedMinor", a.postedMinor());
    m.put("heldMinor", a.heldMinor());
    m.put("availableMinor", a.availableMinor());
    return m;
  }

  private static String text(JsonNode n, String f) {
    JsonNode v = n.get(f);
    if (v == null || !v.isString()) {
      throw LedgerException.invalid(f + " is required");
    }
    return v.asString();
  }

  private static UUID uuid(JsonNode n, String f) {
    try {
      return UUID.fromString(text(n, f));
    } catch (IllegalArgumentException e) {
      throw LedgerException.invalid(f + " must be a UUID");
    }
  }

  private static long amount(JsonNode n, String f) {
    JsonNode v = n.get(f);
    if (v == null || !v.isIntegralNumber()) {
      throw LedgerException.invalid(f + " must be an integer in minor units");
    }
    return v.asLong();
  }
}
