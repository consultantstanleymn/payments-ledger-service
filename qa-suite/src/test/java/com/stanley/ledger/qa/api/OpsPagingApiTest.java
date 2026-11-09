package com.stanley.ledger.qa.api;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.qa.support.ApiClient;
import com.stanley.ledger.qa.support.LedgerDb;
import com.stanley.ledger.qa.support.RunId;
import com.stanley.ledger.qa.support.TestData;
import com.stanley.ledger.qa.support.TestData.Account;
import com.stanley.ledger.qa.support.TestData.Payment;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpsPagingApiTest {

  private final ApiClient api = new ApiClient();
  private final LedgerDb db = new LedgerDb();
  private final TestData data = new TestData(api);

  @Test
  void walkAllPagesNoDuplicatesNoGaps() {
    String q = subsetQ("walk");
    Set<String> expectedIds = new HashSet<>();

    for (int i = 1; i <= 23; i++) {
      Account account = namedAccount("walk", i);
      expectedIds.add(account.id().toString());
    }

    List<String> seenIds = new ArrayList<>();
    String cursor = null;
    int seenBefore = 0;
    int pageNumber = 0;

    while (true) {
      Response response = getAccountsPage(q, 5, cursor);
      validatePage(response);
      List<Map<String, Object>> items = itemList(response);

      assertThat(items)
          .as("page %d item count", pageNumber + 1)
          .hasSize(Math.min(5, 23 - seenBefore));

      for (Map<String, Object> item : items) {
        seenIds.add(accountId(item));
      }

      seenBefore += items.size();
      pageNumber++;
      cursor = response.jsonPath().getString("nextCursor");

      if (cursor == null) {
        break;
      }
    }

    assertThat(pageNumber).isEqualTo(5);
    assertThat(seenIds).hasSize(23).doesNotHaveDuplicates();
    assertThat(seenIds).containsExactlyInAnyOrderElementsOf(expectedIds);
  }

  @Test
  void accountCreatedBetweenPagesAppearsOnlyAtTop() {
    String q = subsetQ("between");

    for (int i = 1; i <= 5; i++) {
      namedAccount("between", i);
    }

    Response first = getAccountsPage(q, 3, null);
    validatePage(first);
    assertThat(itemList(first)).hasSize(3);
    String firstCursor = first.jsonPath().getString("nextCursor");
    assertThat(firstCursor).isNotNull();

    Account inserted = namedAccount("between", 6);

    Response second = getAccountsPage(q, 3, firstCursor);
    validatePage(second);
    assertThat(accountIds(second)).doesNotContain(inserted.id().toString());

    Response freshFirst = getAccountsPage(q, 3, null);
    validatePage(freshFirst);
    List<String> freshFirstIds = accountIds(freshFirst);
    assertThat(freshFirstIds).isNotEmpty();
    assertThat(freshFirstIds.get(0)).isEqualTo(inserted.id().toString());

    List<String> allIds = collectAccountIds(q, 3);
    assertThat(allIds).containsOnlyOnce(inserted.id().toString());
  }

  @Test
  void badCursor400() {
    Response response =
        api.request()
            .queryParam("q", subsetQ("cursor"))
            .queryParam("limit", 5)
            .queryParam("cursor", "not-a-cursor")
            .get("/ops/accounts");

    assertThat(response.statusCode()).isEqualTo(400);
  }

  @Test
  void limitBounds() {
    String q = subsetQ("limit");
    namedAccount("limit", 1);

    Response lowest = getAccountsPage(q, 1, null);
    validatePage(lowest);
    assertThat(itemList(lowest)).hasSize(1);

    Response highest = getAccountsPage(q, 100, null);
    validatePage(highest);

    Response below =
        api.request().queryParam("q", q).queryParam("limit", 0).get("/ops/accounts");
    assertThat(below.statusCode()).isEqualTo(400);

    Response above =
        api.request().queryParam("q", q).queryParam("limit", 101).get("/ops/accounts");
    assertThat(above.statusCode()).isEqualTo(400);
  }

  @Test
  void entriesPageMatchesDbOrder() {
    Account bank =
        data.account()
            .currency("USD")
            .floorMinor(-1_000_000_000L)
            .named(accountName("entries-bank", 1))
            .create();
    Account payer = namedAccount("entries-payer", 2);
    Account payee = namedAccount("entries-payee", 3);

    data.transfer().from(bank).to(payer).amount(10_000).post();
    Payment first = data.payment().from(payer).to(payee).amount(1_200).authorize();
    Payment second = data.payment().from(payer).to(payee).amount(800).authorize();
    data.capture(first);
    data.capture(second);

    List<Long> expected =
        db.entriesFor(payer.id()).stream()
            .map(LedgerDb.JournalEntry::id)
            .sorted(java.util.Comparator.reverseOrder())
            .toList();

    List<Long> actual = collectEntryIds(payer.id(), 2);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  private Account namedAccount(String group, int n) {
    return data.account().currency("USD").named(accountName(group, n)).create();
  }

  private String accountName(String group, int n) {
    return String.format("qa-%s-%s-%d", RunId.value(), group, n);
  }

  private String subsetQ(String group) {
    return "qa-" + RunId.value() + "-" + group;
  }

  private Response getAccountsPage(String q, int limit, String cursor) {
    RequestSpecification request =
        api.request().queryParam("q", q).queryParam("limit", limit);

    if (cursor != null) {
      request = request.queryParam("cursor", cursor);
    }

    return request.get("/ops/accounts");
  }

  private Response entriesPage(UUID accountId, int limit, String cursor) {
    RequestSpecification request = api.request().queryParam("limit", limit);

    if (cursor != null) {
      request = request.queryParam("cursor", cursor);
    }

    return request
        .pathParam("id", accountId.toString())
        .get("/ops/accounts/{id}/entries");
  }

  private List<String> collectAccountIds(String q, int limit) {
    List<String> ids = new ArrayList<>();
    String cursor = null;

    while (true) {
      Response response = getAccountsPage(q, limit, cursor);
      validatePage(response);
      ids.addAll(accountIds(response));
      cursor = response.jsonPath().getString("nextCursor");

      if (cursor == null) {
        break;
      }
    }

    return ids;
  }

  private List<Long> collectEntryIds(UUID accountId, int limit) {
    List<Long> ids = new ArrayList<>();
    String cursor = null;

    while (true) {
      Response response = entriesPage(accountId, limit, cursor);
      validatePage(response);

      for (Map<String, Object> item : itemList(response)) {
        ids.add(entryId(item));
      }

      cursor = response.jsonPath().getString("nextCursor");

      if (cursor == null) {
        break;
      }
    }

    return ids;
  }

  private List<String> accountIds(Response response) {
    List<String> ids = new ArrayList<>();

    for (Map<String, Object> item : itemList(response)) {
      ids.add(accountId(item));
    }

    return ids;
  }

  private void validatePage(Response response) {
    response
        .then()
        .statusCode(200)
        .body(matchesJsonSchemaInClasspath("schemas/ops-page.json"));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> itemList(Response response) {
    List<?> raw = response.jsonPath().getList("items");
    return (List<Map<String, Object>>) raw;
  }

  private static String accountId(Map<String, Object> item) {
    Object id = item.get("id");

    if (id == null) {
      id = item.get("accountId");
    }

    if (id == null) {
      throw new AssertionError("ops account item is missing id and accountId: " + item);
    }

    return id.toString();
  }

  private static long entryId(Map<String, Object> item) {
    Object id = item.get("entryId");

    if (id == null) {
      throw new AssertionError("ops entry item is missing entryId: " + item);
    }

    if (id instanceof Number number) {
      return number.longValue();
    }

    if (id instanceof String value) {
      return Long.parseLong(value);
    }

    throw new AssertionError("ops entry id has unexpected type: " + id);
  }
}
