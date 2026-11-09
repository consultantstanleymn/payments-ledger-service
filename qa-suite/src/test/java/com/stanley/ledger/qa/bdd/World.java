package com.stanley.ledger.qa.bdd;

import com.stanley.ledger.qa.support.ApiClient;
import com.stanley.ledger.qa.support.LedgerDb;
import com.stanley.ledger.qa.support.RunId;
import com.stanley.ledger.qa.support.TestData;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * State for one scenario. Cucumber with picocontainer makes a new World for each scenario, so
 * parallel scenarios never see each other's accounts or payments.
 */
public final class World {
  private final ApiClient api = new ApiClient();
  private final TestData data = new TestData(api);
  private final LedgerDb db = new LedgerDb();
  private final Map<String, TestData.Account> accounts = new LinkedHashMap<>();
  private final Set<UUID> transactionIds = new LinkedHashSet<>();
  private static final AtomicInteger KEYS = new AtomicInteger();

  private TestData.Payment payment;
  private Response lastResponse;
  private Response firstAuthorizeResponse;
  private String lastAuthorizeKey;
  private String lastAuthorizeBody;
  private Balance recordedSenderBalance;

  public ApiClient api() {
    return api;
  }

  public TestData data() {
    return data;
  }

  public LedgerDb db() {
    return db;
  }

  /** A unique request key inside this JVM. The run id keeps separate runs apart. */
  public String key(String purpose) {
    return RunId.idempotencyKey(purpose + "-" + KEYS.incrementAndGet());
  }

  public void account(String role, TestData.Account account) {
    accounts.put(role, account);
  }

  public TestData.Account account(String role) {
    TestData.Account account = accounts.get(role);
    if (account == null) {
      throw new IllegalStateException("no " + role + " account in this scenario");
    }
    return account;
  }

  public Set<String> accountRoles() {
    return accounts.keySet();
  }

  /** The funding account. It has a very low floor, so it can move any test amount. */
  public TestData.Account bank() {
    if (!accounts.containsKey("bank")) {
      account("bank", data.account().floorMinor(-1_000_000L).create());
    }
    return account("bank");
  }

  public void addTransaction(UUID id) {
    if (id != null) {
      transactionIds.add(id);
    }
  }

  public List<UUID> transactionIds() {
    return new ArrayList<>(transactionIds);
  }

  /** Reads the journal transactions that belong to the current payment. */
  public void recordTransactionsForPayment() {
    if (payment != null) {
      transactionIds.addAll(db.transactionIdsForReference(payment.id()));
    }
  }

  public TestData.Payment payment() {
    return payment;
  }

  public void payment(TestData.Payment payment) {
    this.payment = payment;
  }

  public Response lastResponse() {
    return lastResponse;
  }

  public void lastResponse(Response response) {
    this.lastResponse = response;
  }

  public Response firstAuthorizeResponse() {
    return firstAuthorizeResponse;
  }

  public void firstAuthorizeResponse(Response response) {
    this.firstAuthorizeResponse = response;
  }

  public String lastAuthorizeKey() {
    return lastAuthorizeKey;
  }

  public void lastAuthorizeKey(String key) {
    this.lastAuthorizeKey = key;
  }

  public String lastAuthorizeBody() {
    return lastAuthorizeBody;
  }

  public void lastAuthorizeBody(String body) {
    this.lastAuthorizeBody = body;
  }

  public Balance balance(String role) {
    TestData.Account account = account(role);
    Response response = api.request().get("/accounts/{id}/balance", account.id().toString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "balance read failed with HTTP "
              + response.statusCode()
              + ": "
              + response.body().asString());
    }
    JsonPath json = response.jsonPath();
    return new Balance(
        json.getLong("postedMinor"), json.getLong("heldMinor"), json.getLong("availableMinor"));
  }

  public void recordSenderBalance() {
    recordedSenderBalance = balance("sender");
  }

  public Balance recordedSenderBalance() {
    if (recordedSenderBalance == null) {
      throw new IllegalStateException("the sender balance was not recorded");
    }
    return recordedSenderBalance;
  }

  /** Builds a payment view from a raw response, so a rejected call can be tested too. */
  public TestData.Payment paymentFrom(Response response) {
    JsonPath json = response.jsonPath();
    return new TestData.Payment(
        UUID.fromString(json.getString("id")),
        UUID.fromString(json.getString("payerId")),
        UUID.fromString(json.getString("payeeId")),
        json.getString("currency"),
        json.getLong("authorizedMinor"),
        json.getLong("capturedMinor"),
        json.getLong("refundedMinor"),
        json.getString("status"),
        json.getString("externalRef"));
  }

  public record Balance(long postedMinor, long heldMinor, long availableMinor) {}
}
