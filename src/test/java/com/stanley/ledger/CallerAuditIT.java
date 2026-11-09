package com.stanley.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

class CallerAuditIT extends IntegrationBase {
  @Value("${local.server.port}")
  int port;

  final HttpClient http = HttpClient.newHttpClient();

  HttpResponse<String> call(String method, String path, String apiKey, String idemKey, String body)
      throws Exception {
    HttpRequest.Builder b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
    if (apiKey != null) b.header("X-Api-Key", apiKey);
    if (idemKey != null) b.header("Idempotency-Key", idemKey);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  /** The filter writes after the response, so wait until the expected row count is there. */
  List<Map<String, Object>> awaitRows(long expected) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 15_000;
    while (System.currentTimeMillis() < deadline) {
      var rows = jdbc.sql("SELECT * FROM caller_audit ORDER BY id").query().listOfRows();
      if (rows.size() >= expected) return rows;
      Thread.sleep(100);
    }
    return jdbc.sql("SELECT * FROM caller_audit ORDER BY id").query().listOfRows();
  }

  String transfer(List<UUID> ids) {
    return "{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amountMinor\":100}"
        .formatted(ids.get(1), ids.get(2));
  }

  @Test
  void writesOneRowPerRequestWithRoutePattern() throws Exception {
    List<UUID> ids = fundedAccounts(2, 10_000);
    call("POST", "/transfers", "dev-key-local-only", "audit-1", transfer(ids));
    call("GET", "/accounts/" + ids.get(1) + "/balance", "dev-key-local-only", null, null);
    var rows = awaitRows(2);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)).containsEntry("route", "/transfers").containsEntry("method", "POST");
    assertThat(rows.get(1)).containsEntry("route", "/accounts/{id}/balance");
  }

  @Test
  void storesClientNameNotApiKey() throws Exception {
    List<UUID> ids = fundedAccounts(2, 10_000);
    call("POST", "/transfers", "dev-key-local-only", "audit-2", transfer(ids));
    var rows = awaitRows(1);
    assertThat(rows.get(0)).containsEntry("client_id", "dev");
    assertThat(rows.toString()).doesNotContain("dev-key-local-only");
  }

  @Test
  void recordsIdempotencyKeyAndStatus() throws Exception {
    List<UUID> ids = fundedAccounts(2, 10_000);
    call("POST", "/transfers", "dev-key-local-only", "audit-3", transfer(ids));
    call("POST", "/transfers", "dev-key-local-only", "audit-3", transfer(ids));
    var rows = awaitRows(2);
    assertThat(rows.get(0))
        .containsEntry("idempotency_key", "audit-3")
        .containsEntry("status", 201)
        .containsEntry("outcome", "ok");
    assertThat(rows.get(1)).containsEntry("outcome", "replay");
  }

  @Test
  void updateAndDeleteAreRejected() throws Exception {
    List<UUID> ids = fundedAccounts(2, 10_000);
    call("POST", "/transfers", "dev-key-local-only", "audit-4", transfer(ids));
    awaitRows(1);
    assertThatThrownBy(() -> jdbc.sql("UPDATE caller_audit SET status = 200").update())
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.sql("DELETE FROM caller_audit").update())
        .hasMessageContaining("append-only");
  }

  @Test
  void unauthorizedRequestsAreAuditedAsAnonymous() throws Exception {
    var res = call("POST", "/transfers", "wrong-key", "audit-5", "{}");
    assertThat(res.statusCode()).isEqualTo(401);
    var rows = awaitRows(1);
    assertThat(rows.get(0)).containsEntry("client_id", "anonymous").containsEntry("status", 401);
    assertThat(rows.get(0)).containsEntry("outcome", "rejected");
  }
}
