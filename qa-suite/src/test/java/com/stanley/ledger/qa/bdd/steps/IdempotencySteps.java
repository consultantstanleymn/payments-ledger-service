package com.stanley.ledger.qa.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.qa.bdd.World;
import com.stanley.ledger.qa.support.RunId;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;

/** Retries with the same idempotency key, and the conflict that a different request causes. */
public class IdempotencySteps {
  private final World world;

  public IdempotencySteps(World world) {
    this.world = world;
  }

  @When(
      "the payer authorizes a payment of {long} minor units to the payee with idempotency key"
          + " {string} and external reference {string}")
  public void authorizeWithReference(long amount, String logicalKey, String logicalReference) {
    String key = RunId.idempotencyKey(logicalKey);
    String body =
        String.format(
            "{\"payerAccountId\":\"%s\",\"payeeAccountId\":\"%s\",\"amountMinor\":%d,"
                + "\"externalRef\":\"%s\"}",
            world.account("payer").id(),
            world.account("payee").id(),
            amount,
            RunId.value() + "-" + logicalReference);
    world.lastAuthorizeKey(key);
    world.lastAuthorizeBody(body);
    Response response = world.api().withIdempotency(key).body(body).post("/payments");
    world.firstAuthorizeResponse(response);
    world.lastResponse(response);
    if (response.statusCode() == 201) {
      world.payment(world.paymentFrom(response));
    }
  }

  @When("the payer repeats the last authorization request")
  public void repeatLastAuthorization() {
    Response response =
        world
            .api()
            .withIdempotency(world.lastAuthorizeKey())
            .body(world.lastAuthorizeBody())
            .post("/payments");
    world.lastResponse(response);
    if (response.statusCode() == 201) {
      world.payment(world.paymentFrom(response));
    }
  }

  @When("the payer authorizes a payment of {long} minor units to the payee with idempotency key {string}")
  public void authorizeWithKey(long amount, String logicalKey) {
    String key = RunId.idempotencyKey(logicalKey);
    String body =
        String.format(
            "{\"payerAccountId\":\"%s\",\"payeeAccountId\":\"%s\",\"amountMinor\":%d}",
            world.account("payer").id(), world.account("payee").id(), amount);
    Response response = world.api().withIdempotency(key).body(body).post("/payments");
    world.lastResponse(response);
    if (response.statusCode() == 201) {
      world.payment(world.paymentFrom(response));
    }
  }

  @Then("both responses hold the same payment identifier")
  public void bothResponsesHoldTheSamePaymentIdentifier() {
    assertThat(world.firstAuthorizeResponse()).isNotNull();
    assertThat(world.lastResponse()).isNotNull();
    assertThat(world.firstAuthorizeResponse().statusCode()).isEqualTo(201);
    assertThat(world.lastResponse().statusCode()).isEqualTo(201);
    assertThat(world.lastResponse().jsonPath().getString("id"))
        .isEqualTo(world.firstAuthorizeResponse().jsonPath().getString("id"));
  }

  @Then("the second response is a replay")
  public void theSecondResponseIsAReplay() {
    assertThat(world.lastResponse().header("Idempotent-Replay")).isEqualTo("true");
  }
}
