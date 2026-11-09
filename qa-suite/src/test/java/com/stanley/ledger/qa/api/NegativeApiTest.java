package com.stanley.ledger.qa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.qa.config.QaConfig;
import com.stanley.ledger.qa.support.ApiClient;
import com.stanley.ledger.qa.support.RunId;
import com.stanley.ledger.qa.support.TestData;
import com.stanley.ledger.qa.support.TestData.Account;
import com.stanley.ledger.qa.support.TestData.Payment;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Negative API behaviour. Each test checks the status and the error code the service returns. */
class NegativeApiTest {
  private final ApiClient api = new ApiClient();
  private final TestData data = new TestData(api);

  private static String accountBody(String name) {
    return String.format("{\"name\":\"%s\",\"currency\":\"USD\",\"floorMinor\":0}", name);
  }

  private static String authorizeBody(UUID payer, UUID payee, long amount) {
    return String.format(
        "{\"payerAccountId\":\"%s\",\"payeeAccountId\":\"%s\",\"amountMinor\":%d}",
        payer, payee, amount);
  }

  private void assertError(Response response, int status, String code) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.jsonPath().getString("code")).isEqualTo(code);
    assertThat(response.jsonPath().getString("message")).isNotBlank();
  }

  @Test
  void missingApiKey401() {
    Response response =
        RestAssured.given()
            .baseUri(QaConfig.ledgerUrl())
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", api.newIdempotencyKey())
            .body(accountBody(RunId.accountName(10)))
            .post("/accounts");

    assertError(response, 401, "UNAUTHORIZED");
  }

  @Test
  void badApiKey401() {
    Response response =
        RestAssured.given()
            .baseUri(QaConfig.ledgerUrl())
            .contentType(ContentType.JSON)
            .header("X-Api-Key", "not-the-key")
            .header("Idempotency-Key", api.newIdempotencyKey())
            .body(accountBody(RunId.accountName(11)))
            .post("/accounts");

    assertError(response, 401, "UNAUTHORIZED");
  }

  @Test
  void malformedJson400() {
    Response response =
        api.withIdempotency(api.newIdempotencyKey()).body("{ this is not json").post("/accounts");

    assertError(response, 400, "BAD_JSON");
  }

  @Test
  void unknownPayment404() {
    Response response = api.request().get("/payments/{id}", UUID.randomUUID().toString());

    assertError(response, 404, "NOT_FOUND");
  }

  @Test
  void captureMoreThanAuthorizedRejected() {
    Account bank = data.account().floorMinor(-1_000_000L).create();
    Account payer = data.account().create();
    Account payee = data.account().create();
    data.transfer().from(bank).to(payer).amount(5_000).post();
    Payment authorized = data.payment().from(payer).to(payee).amount(1_000).authorize();

    Response response =
        api.withIdempotency(api.newIdempotencyKey())
            .body("{\"amountMinor\":1001}")
            .post("/payments/{id}/capture", authorized.id().toString());

    assertError(response, 422, "INVALID_REQUEST");
  }

  @Test
  void refundMoreThanCapturedRejected() {
    Account bank = data.account().floorMinor(-1_000_000L).create();
    Account payer = data.account().create();
    Account payee = data.account().create();
    data.transfer().from(bank).to(payer).amount(5_000).post();
    Payment authorized = data.payment().from(payer).to(payee).amount(1_000).authorize();
    data.capture(authorized);

    Response response =
        api.withIdempotency(api.newIdempotencyKey())
            .body("{\"amountMinor\":1001}")
            .post("/payments/{id}/refund", authorized.id().toString());

    assertError(response, 422, "INVALID_REQUEST");
  }

  @Test
  void negativeAmountRejected() {
    Account payer = data.account().create();
    Account payee = data.account().create();

    Response response =
        api.withIdempotency(api.newIdempotencyKey())
            .body(authorizeBody(payer.id(), payee.id(), -100))
            .post("/payments");

    assertError(response, 422, "INVALID_REQUEST");
  }

  @Test
  void currencyMismatchRejected() {
    Account payer = data.account().currency("USD").create();
    Account payee = data.account().currency("EUR").create();

    Response response =
        api.withIdempotency(api.newIdempotencyKey())
            .body(authorizeBody(payer.id(), payee.id(), 100))
            .post("/payments");

    assertError(response, 422, "INVALID_REQUEST");
  }
}
