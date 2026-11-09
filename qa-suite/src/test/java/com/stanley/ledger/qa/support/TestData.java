package com.stanley.ledger.qa.support;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fluent builders that create test data through the public API. The service owns every write, so
 * no builder inserts rows with SQL. Account names carry the run id, which keeps parallel runs
 * apart.
 */
public final class TestData {
  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  private final ApiClient api;

  public TestData() {
    this(new ApiClient());
  }

  public TestData(ApiClient api) {
    this.api = api;
  }

  public AccountBuilder account() {
    return new AccountBuilder();
  }

  public PaymentBuilder payment() {
    return new PaymentBuilder();
  }

  public TransferBuilder transfer() {
    return new TransferBuilder();
  }

  public Payment capture(Payment payment) {
    return capture(payment, payment.authorizedMinor());
  }

  public Payment capture(Payment payment, long amount) {
    String body = String.format("{\"amountMinor\":%d}", amount);
    Response response =
        api.withIdempotency(key("capture"))
            .body(body)
            .post("/payments/{id}/capture", payment.id().toString());
    requireStatus(response, 200);
    return toPayment(response);
  }

  public Payment refund(Payment payment, long amount) {
    String body = String.format("{\"amountMinor\":%d}", amount);
    Response response =
        api.withIdempotency(key("refund"))
            .body(body)
            .post("/payments/{id}/refund", payment.id().toString());
    requireStatus(response, 200);
    return toPayment(response);
  }

  public Payment voidPayment(Payment payment) {
    Response response =
        api.withIdempotency(key("void"))
            .body("{}")
            .post("/payments/{id}/void", payment.id().toString());
    requireStatus(response, 200);
    return toPayment(response);
  }

  public Payment payment(UUID id) {
    Response response = api.request().get("/payments/{id}", id.toString());
    requireStatus(response, 200);
    return toPayment(response);
  }

  private static String key(String purpose) {
    return RunId.idempotencyKey(purpose + "-" + SEQUENCE.incrementAndGet());
  }

  private static void requireStatus(Response response, int expected) {
    if (response.statusCode() != expected) {
      throw new IllegalStateException(
          "expected HTTP "
              + expected
              + " but got "
              + response.statusCode()
              + ": "
              + response.body().asString());
    }
  }

  private static Payment toPayment(Response response) {
    JsonPath json = response.jsonPath();
    String externalRef = json.getString("externalRef");
    return new Payment(
        UUID.fromString(json.getString("id")),
        UUID.fromString(json.getString("payerId")),
        UUID.fromString(json.getString("payeeId")),
        json.getString("currency"),
        json.getLong("authorizedMinor"),
        json.getLong("capturedMinor"),
        json.getLong("refundedMinor"),
        json.getString("status"),
        externalRef);
  }

  public final class AccountBuilder {
    private String currency = "USD";
    private long floorMinor;
    private String name = "qa-" + RunId.value() + "-" + SEQUENCE.incrementAndGet();

    public AccountBuilder currency(String value) {
      this.currency = value;
      return this;
    }

    public AccountBuilder floorMinor(long value) {
      this.floorMinor = value;
      return this;
    }

    public AccountBuilder named(String value) {
      this.name = value;
      return this;
    }

    public Account create() {
      String body =
          String.format(
              "{\"name\":\"%s\",\"currency\":\"%s\",\"floorMinor\":%d}",
              name, currency, floorMinor);
      Response response = api.withIdempotency(key("account")).body(body).post("/accounts");
      requireStatus(response, 201);
      JsonPath json = response.jsonPath();
      return new Account(
          UUID.fromString(json.getString("accountId")),
          name,
          json.getString("currency"),
          json.getLong("floorMinor"),
          json.getLong("postedMinor"),
          json.getLong("heldMinor"),
          json.getLong("availableMinor"));
    }
  }

  public final class PaymentBuilder {
    private Account from;
    private Account to;
    private long amount;
    private String externalRef;

    public PaymentBuilder from(Account account) {
      this.from = account;
      return this;
    }

    public PaymentBuilder to(Account account) {
      this.to = account;
      return this;
    }

    public PaymentBuilder amount(long value) {
      this.amount = value;
      return this;
    }

    public PaymentBuilder externalRef(String value) {
      this.externalRef = value;
      return this;
    }

    public Payment authorize() {
      if (from == null || to == null) {
        throw new IllegalStateException("payment needs a source and a destination account");
      }
      String suffix =
          externalRef == null ? "" : String.format(",\"externalRef\":\"%s\"", externalRef);
      String body =
          String.format(
              "{\"payerAccountId\":\"%s\",\"payeeAccountId\":\"%s\",\"amountMinor\":%d%s}",
              from.id(), to.id(), amount, suffix);
      Response response = api.withIdempotency(key("authorize")).body(body).post("/payments");
      requireStatus(response, 201);
      return toPayment(response);
    }
  }

  public final class TransferBuilder {
    private Account from;
    private Account to;
    private long amount;

    public TransferBuilder from(Account account) {
      this.from = account;
      return this;
    }

    public TransferBuilder to(Account account) {
      this.to = account;
      return this;
    }

    public TransferBuilder amount(long value) {
      this.amount = value;
      return this;
    }

    public Transfer post() {
      if (from == null || to == null) {
        throw new IllegalStateException("transfer needs a source and a destination account");
      }
      String body =
          String.format(
              "{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amountMinor\":%d}",
              from.id(), to.id(), amount);
      Response response = api.withIdempotency(key("transfer")).body(body).post("/transfers");
      requireStatus(response, 201);
      UUID transactionId = UUID.fromString(response.jsonPath().getString("transactionId"));
      return new Transfer(transactionId);
    }
  }

  public record Account(
      UUID id,
      String name,
      String currency,
      long floorMinor,
      long postedMinor,
      long heldMinor,
      long availableMinor) {}

  public record Payment(
      UUID id,
      UUID payerId,
      UUID payeeId,
      String currency,
      long authorizedMinor,
      long capturedMinor,
      long refundedMinor,
      String status,
      String externalRef) {}

  public record Transfer(UUID transactionId) {}
}
