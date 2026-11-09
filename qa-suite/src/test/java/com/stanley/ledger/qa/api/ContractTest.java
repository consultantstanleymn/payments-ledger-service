package com.stanley.ledger.qa.api;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.qa.config.QaConfig;
import com.stanley.ledger.qa.support.ApiClient;
import com.stanley.ledger.qa.support.RunId;
import com.stanley.ledger.qa.support.TestData;
import com.stanley.ledger.qa.support.TestData.Account;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract checks: every response body must match the schema that the API documents. */
class ContractTest {
  private final ApiClient api = new ApiClient();
  private final TestData data = new TestData(api);

  private Response createAccount(String name) {
    String body = String.format("{\"name\":\"%s\",\"currency\":\"USD\",\"floorMinor\":0}", name);
    return api.withIdempotency(api.newIdempotencyKey()).body(body).post("/accounts");
  }

  private Response authorize(UUID payer, UUID payee, long amount) {
    String body =
        String.format(
            "{\"payerAccountId\":\"%s\",\"payeeAccountId\":\"%s\",\"amountMinor\":%d}",
            payer, payee, amount);
    return api.withIdempotency(api.newIdempotencyKey()).body(body).post("/payments");
  }

  private Response capture(String paymentId, long amount) {
    return api.withIdempotency(api.newIdempotencyKey())
        .body(String.format("{\"amountMinor\":%d}", amount))
        .post("/payments/{id}/capture", paymentId);
  }

  private Response refund(String paymentId, long amount) {
    return api.withIdempotency(api.newIdempotencyKey())
        .body(String.format("{\"amountMinor\":%d}", amount))
        .post("/payments/{id}/refund", paymentId);
  }

  private Response voidPayment(String paymentId) {
    return api.withIdempotency(api.newIdempotencyKey())
        .body("{}")
        .post("/payments/{id}/void", paymentId);
  }

  @Test
  void accountResponsesMatchSchema() {
    Response created = createAccount(RunId.accountName(1));
    created
        .then()
        .statusCode(201)
        .body(matchesJsonSchemaInClasspath("schemas/account.json"));

    Response balance =
        api.request()
            .get("/accounts/{id}/balance", created.jsonPath().getString("accountId"));
    balance.then().statusCode(200).body(matchesJsonSchemaInClasspath("schemas/balance.json"));
  }

  @Test
  void paymentResponsesMatchSchema() {
    Account bank = data.account().floorMinor(-1_000_000L).create();
    Account payer = data.account().create();
    Account payee = data.account().create();
    data.transfer().from(bank).to(payer).amount(10_000).post();

    Response authorized = authorize(payer.id(), payee.id(), 800);
    authorized
        .then()
        .statusCode(201)
        .body(matchesJsonSchemaInClasspath("schemas/payment.json"));
    String paymentId = authorized.jsonPath().getString("id");

    capture(paymentId, 500)
        .then()
        .statusCode(200)
        .body(matchesJsonSchemaInClasspath("schemas/payment.json"));

    refund(paymentId, 100)
        .then()
        .statusCode(200)
        .body(matchesJsonSchemaInClasspath("schemas/payment.json"));

    api.request()
        .get("/payments/{id}", paymentId)
        .then()
        .statusCode(200)
        .body(matchesJsonSchemaInClasspath("schemas/payment.json"));

    String toVoid = authorize(payer.id(), payee.id(), 100).jsonPath().getString("id");
    voidPayment(toVoid)
        .then()
        .statusCode(200)
        .body(matchesJsonSchemaInClasspath("schemas/payment.json"));
  }

  @Test
  void errorResponsesMatchSchema() {
    Response missingKey =
        RestAssured.given()
            .baseUri(QaConfig.ledgerUrl())
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", api.newIdempotencyKey())
            .body("{\"name\":\"qa-contract-401\",\"currency\":\"USD\"}")
            .post("/accounts");
    missingKey
        .then()
        .statusCode(401)
        .body(matchesJsonSchemaInClasspath("schemas/error.json"));

    Response badJson =
        api.withIdempotency(api.newIdempotencyKey()).body("{ not json").post("/accounts");
    badJson
        .then()
        .statusCode(400)
        .body(matchesJsonSchemaInClasspath("schemas/error.json"));

    Response unknown =
        api.request().get("/payments/{id}", UUID.randomUUID().toString());
    unknown
        .then()
        .statusCode(404)
        .body(matchesJsonSchemaInClasspath("schemas/error.json"));

    Account bank = data.account().floorMinor(-1_000_000L).create();
    Account payer = data.account().create();
    Account payee = data.account().create();
    data.transfer().from(bank).to(payer).amount(1_000).post();
    Response negative = authorize(payer.id(), payee.id(), -100);
    negative
        .then()
        .statusCode(422)
        .body(matchesJsonSchemaInClasspath("schemas/error.json"));
  }
}
