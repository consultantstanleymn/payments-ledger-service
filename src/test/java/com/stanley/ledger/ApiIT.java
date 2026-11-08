package com.stanley.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ApiIT extends IntegrationBase {
  @Value("${local.server.port}")
  int port;

  final HttpClient http = HttpClient.newHttpClient();
  final JsonMapper json = JsonMapper.builder().build();

  HttpResponse<String> post(String path, String key, String body, String apiKey) throws Exception {
    HttpRequest.Builder b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (apiKey != null) b.header("X-Api-Key", apiKey);
    if (key != null) b.header("Idempotency-Key", key);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> post(String path, String key, String body) throws Exception {
    return post(path, key, body, "dev-key-local-only");
  }

  long count(String table) {
    return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
  }

  String transferBody(List<UUID> ids, long amount) {
    return "{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amountMinor\":%d}"
        .formatted(ids.get(1), ids.get(2), amount);
  }

  @Test
  void sameKeySameBodyReplaysTheResponseAndPostsOnce() throws Exception {
    List<UUID> ids = fundedAccounts(2, 10_000);
    long txnsBefore = count("journal_transactions");
    var first = post("/transfers", "k-1", transferBody(ids, 500));
    var second = post("/transfers", "k-1", transferBody(ids, 500));
    assertThat(first.statusCode()).isEqualTo(201);
    assertThat(second.statusCode()).isEqualTo(201);
    assertThat(second.body()).isEqualTo(first.body());
    assertThat(second.headers().firstValue("Idempotent-Replay")).contains("true");
    assertThat(count("journal_transactions") - txnsBefore).isEqualTo(1);
  }

  @Test
  void sameKeyDifferentBodyReturns422() throws Exception {
    List<UUID> ids = fundedAccounts(2, 10_000);
    assertThat(post("/transfers", "k-2", transferBody(ids, 500)).statusCode()).isEqualTo(201);
    var clash = post("/transfers", "k-2", transferBody(ids, 600));
    assertThat(clash.statusCode()).isEqualTo(422);
    assertThat(clash.body()).contains("IDEMPOTENCY_KEY_REUSED");
  }

  @Test
  void twoParallelRequestsWithOneKeyPostOnce() throws Exception {
    List<UUID> ids = fundedAccounts(2, 10_000);
    long before = count("journal_transactions");
    var a =
        CompletableFuture.supplyAsync(
            () -> call(() -> post("/transfers", "k-3", transferBody(ids, 100))));
    var b =
        CompletableFuture.supplyAsync(
            () -> call(() -> post("/transfers", "k-3", transferBody(ids, 100))));
    assertThat(a.get().body()).isEqualTo(b.get().body());
    assertThat(count("journal_transactions") - before).isEqualTo(1);
  }

  interface Call {
    HttpResponse<String> run() throws Exception;
  }

  static HttpResponse<String> call(Call c) {
    try {
      return c.run();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void writesNeedAnIdempotencyKeyAndAnApiKey() throws Exception {
    List<UUID> ids = fundedAccounts(2, 10_000);
    assertThat(post("/transfers", null, transferBody(ids, 1)).statusCode()).isEqualTo(400);
    assertThat(post("/transfers", "k-4", transferBody(ids, 1), null).statusCode()).isEqualTo(401);
    assertThat(post("/transfers", "k-5", transferBody(ids, 1), "wrong").statusCode())
        .isEqualTo(401);
  }

  @Test
  void paymentLifecycleAndBalances() throws Exception {
    List<UUID> ids = fundedAccounts(2, 10_000);
    var auth =
        post(
            "/payments",
            "p-1",
            "{\"payerAccountId\":\"%s\",\"payeeAccountId\":\"%s\",\"amountMinor\":4000}"
                .formatted(ids.get(1), ids.get(2)));
    assertThat(auth.statusCode()).isEqualTo(201);
    String pid = json.readTree(auth.body()).get("id").asString();
    JsonNode held = json.readTree(get("/accounts/" + ids.get(1) + "/balance"));
    assertThat(held.get("availableMinor").asLong()).isEqualTo(6000);
    assertThat(held.get("postedMinor").asLong()).isEqualTo(10_000);

    var cap = post("/payments/" + pid + "/capture", "p-2", "{\"amountMinor\":3500}");
    assertThat(cap.statusCode()).isEqualTo(200);
    JsonNode payer = json.readTree(get("/accounts/" + ids.get(1) + "/balance"));
    JsonNode payee = json.readTree(get("/accounts/" + ids.get(2) + "/balance"));
    assertThat(payer.get("postedMinor").asLong()).isEqualTo(6500);
    assertThat(payer.get("heldMinor").asLong()).isZero();
    assertThat(payee.get("postedMinor").asLong()).isEqualTo(13_500);

    assertThat(post("/payments/" + pid + "/refund", "p-3", "{\"amountMinor\":500}").statusCode())
        .isEqualTo(200);
    assertThat(post("/payments/" + pid + "/void", "p-4", "{}").statusCode()).isEqualTo(409);
    assertThat(post("/payments/" + pid + "/refund", "p-5", "{\"amountMinor\":99999}").statusCode())
        .isEqualTo(422);
  }

  String get(String path) throws Exception {
    return http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Api-Key", "dev-key-local-only")
                .build(),
            HttpResponse.BodyHandlers.ofString())
        .body();
  }
}
