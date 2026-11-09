package com.stanley.ledger.ops;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.IntegrationBase;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OpsQueryIT extends IntegrationBase {
  @Value("${local.server.port}")
  int port;

  final HttpClient http = HttpClient.newHttpClient();
  final JsonMapper json = JsonMapper.builder().build();

  HttpResponse<String> get(String path, String apiKey) throws Exception {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (apiKey != null) b.header("X-Api-Key", apiKey);
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> get(String path) throws Exception {
    return get(path, "dev-key-local-only");
  }

  JsonNode body(HttpResponse<String> res) {
    return json.readTree(res.body());
  }

  static List<UUID> ids(JsonNode page) {
    List<UUID> out = new ArrayList<>();
    for (JsonNode item : page.get("items")) out.add(UUID.fromString(item.get("id").asString()));
    return out;
  }

  static String nextCursor(JsonNode page) {
    JsonNode next = page.get("nextCursor");
    return next == null || next.isNull() ? null : next.asString();
  }

  List<UUID> orderedAccounts() {
    return jdbc.sql("SELECT id FROM accounts ORDER BY created_at DESC, id DESC")
        .query(UUID.class)
        .list();
  }

  @Test
  void accountsPagesDoNotSkipOrRepeatWithTies() throws Exception {
    for (int i = 0; i < 25; i++) {
      ledger.createAccount(String.format("acct-%02d", i), "USD", 0);
    }
    jdbc.sql(
            "UPDATE accounts SET created_at = timestamptz '2026-01-01 00:00:00+00'"
                + " WHERE id IN (SELECT id FROM accounts ORDER BY created_at DESC, id DESC LIMIT 8)")
        .update();
    List<UUID> expected = orderedAccounts();

    List<UUID> actual = new ArrayList<>();
    String cursor = null;
    do {
      String path = "/ops/accounts?limit=6" + (cursor == null ? "" : "&cursor=" + cursor);
      HttpResponse<String> res = get(path);
      assertThat(res.statusCode()).isEqualTo(200);
      JsonNode page = body(res);
      actual.addAll(ids(page));
      cursor = nextCursor(page);
    } while (cursor != null);

    assertThat(actual).containsExactlyElementsOf(expected);
  }

  @Test
  void rowInsertedBeforeCursorDoesNotShiftNextPage() throws Exception {
    for (int i = 0; i < 10; i++) {
      ledger.createAccount("acct-" + i, "USD", 0);
    }
    List<UUID> expected = orderedAccounts();

    JsonNode first = body(get("/ops/accounts?limit=4"));
    String cursor = nextCursor(first);
    assertThat(cursor).isNotNull();
    List<UUID> expectedSecond = expected.subList(4, 8);

    UUID inserted = ledger.createAccount("acct-inserted", "USD", 0).id();
    jdbc.sql("UPDATE accounts SET created_at = timestamptz '2026-12-31 00:00:00+00' WHERE id = :i")
        .param("i", inserted)
        .update();
    JsonNode second = body(get("/ops/accounts?limit=4&cursor=" + cursor));

    assertThat(ids(second)).containsExactlyElementsOf(expectedSecond);
    assertThat(ids(second)).doesNotContain(inserted);
  }

  @Test
  void rowDeletedBetweenPagesDoesNotBreakPaging() throws Exception {
    for (int i = 0; i < 10; i++) {
      ledger.createAccount("acct-" + i, "USD", 0);
    }
    List<UUID> expected = orderedAccounts();

    JsonNode first = body(get("/ops/accounts?limit=4"));
    String cursor = nextCursor(first);
    assertThat(cursor).isNotNull();
    List<UUID> firstIds = ids(first);
    List<UUID> expectedSecond = expected.subList(4, 8);

    UUID anchor = firstIds.get(firstIds.size() - 1);
    jdbc.sql("DELETE FROM accounts WHERE id = :i").param("i", anchor).update();

    HttpResponse<String> res = get("/ops/accounts?limit=4&cursor=" + cursor);
    assertThat(res.statusCode()).isEqualTo(200);
    assertThat(ids(body(res))).containsExactlyElementsOf(expectedSecond);
  }

  @Test
  void accountsFilterByCurrencyAndNamePrefix() throws Exception {
    ledger.createAccount("acme-one", "USD", 0);
    ledger.createAccount("Acme-two", "EUR", 0);
    ledger.createAccount("acme-three", "USD", 0);
    ledger.createAccount("beta", "USD", 0);

    JsonNode page = body(get("/ops/accounts?currency=USD&q=acme"));

    List<String> names = new ArrayList<>();
    for (JsonNode item : page.get("items")) {
      assertThat(item.get("currency").asString()).isEqualTo("USD");
      names.add(item.get("name").asString());
    }
    assertThat(names).containsExactlyInAnyOrder("acme-one", "acme-three");
  }

  @Test
  void namePrefixEscapesWildcards() throws Exception {
    ledger.createAccount("50% off", "USD", 0);
    ledger.createAccount("50 percent", "USD", 0);
    ledger.createAccount("500 club", "USD", 0);

    String q = URLEncoder.encode("50%", StandardCharsets.UTF_8);
    JsonNode page = body(get("/ops/accounts?q=" + q));

    List<String> names = new ArrayList<>();
    for (JsonNode item : page.get("items")) names.add(item.get("name").asString());
    assertThat(names).containsExactly("50% off");
  }

  @Test
  void entriesNewestFirstAndPaged() throws Exception {
    UUID bank = ledger.createAccount("bank", "USD", -1_000_000L).id();
    UUID account = ledger.createAccount("acct", "USD", 0).id();
    ledger.transfer(bank, account, 100);
    ledger.transfer(bank, account, 200);
    ledger.transfer(bank, account, 300);
    ledger.transfer(bank, account, 400);
    List<Long> expected =
        jdbc.sql(
                "SELECT e.id FROM journal_entries e JOIN journal_transactions t ON e.txn_id = t.id"
                    + " WHERE e.account_id = :a ORDER BY e.id DESC")
            .param("a", account)
            .query(Long.class)
            .list();

    List<Long> actual = new ArrayList<>();
    String cursor = null;
    do {
      String path =
          "/ops/accounts/"
              + account
              + "/entries?limit=2"
              + (cursor == null ? "" : "&cursor=" + cursor);
      HttpResponse<String> res = get(path);
      assertThat(res.statusCode()).isEqualTo(200);
      JsonNode page = body(res);
      for (JsonNode item : page.get("items")) {
        actual.add(item.get("entryId").asLong());
        assertThat(item.get("createdAt").asString()).isNotBlank();
      }
      cursor = nextCursor(page);
    } while (cursor != null);

    assertThat(actual).containsExactlyElementsOf(expected);
  }

  @Test
  void entriesUnknownAccount404() throws Exception {
    HttpResponse<String> res = get("/ops/accounts/" + UUID.randomUUID() + "/entries");

    assertThat(res.statusCode()).isEqualTo(404);
    assertThat(body(res).get("code").asString()).isEqualTo("NOT_FOUND");
  }

  @Test
  void paymentsFilterByStatus() throws Exception {
    UUID bank = ledger.createAccount("bank", "USD", -1_000_000L).id();
    UUID a = ledger.createAccount("a", "USD", 0).id();
    UUID b = ledger.createAccount("b", "USD", 0).id();
    ledger.transfer(bank, a, 100_000);
    ledger.authorize(a, b, 1000);
    var captured = ledger.authorize(a, b, 2000);
    ledger.capture(captured.id(), null);
    var voided = ledger.authorize(a, b, 3000);
    ledger.voidPayment(voided.id());

    JsonNode page = body(get("/ops/payments?status=CAPTURED"));

    assertThat(ids(page)).containsExactly(captured.id());
    assertThat(page.get("items").get(0).get("status").asString()).isEqualTo("CAPTURED");
  }

  @Test
  void limitOutOfRange400() throws Exception {
    assertThat(get("/ops/accounts?limit=0").statusCode()).isEqualTo(400);
    assertThat(get("/ops/accounts?limit=101").statusCode()).isEqualTo(400);
  }

  @Test
  void badCursor400() throws Exception {
    String cursor = URLEncoder.encode("%%%", StandardCharsets.UTF_8);

    HttpResponse<String> res = get("/ops/accounts?cursor=" + cursor);

    assertThat(res.statusCode()).isEqualTo(400);
    assertThat(body(res).get("code").asString()).isNotBlank();
  }

  @Test
  void opsRoutesNeedApiKey401() throws Exception {
    assertThat(get("/ops/accounts", null).statusCode()).isEqualTo(401);
  }
}
