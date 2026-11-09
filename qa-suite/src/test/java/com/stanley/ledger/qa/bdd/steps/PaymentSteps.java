package com.stanley.ledger.qa.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.qa.bdd.World;
import com.stanley.ledger.qa.support.TestData;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;

/** Payment actions and the payments side of the assertions. */
public class PaymentSteps {
  private final World world;

  public PaymentSteps(World world) {
    this.world = world;
  }

  @When("the payer authorizes a payment of {long} minor units to the payee")
  public void authorize(long amount) {
    TestData.Payment payment =
        world
            .data()
            .payment()
            .from(world.account("payer"))
            .to(world.account("payee"))
            .amount(amount)
            .authorize();
    world.payment(payment);
  }

  @When("the payment is captured in full")
  public void captureInFull() {
    world.payment(world.data().capture(world.payment()));
    world.recordTransactionsForPayment();
  }

  @When("the payment is captured for {long} minor units")
  public void captureFor(long amount) {
    world.payment(world.data().capture(world.payment(), amount));
    world.recordTransactionsForPayment();
  }

  @When("the payment is voided")
  public void voidPayment() {
    world.payment(world.data().voidPayment(world.payment()));
  }

  @When("the payment is refunded for {long} minor units")
  public void refundFor(long amount) {
    Response response =
        world
            .api()
            .withIdempotency(world.key("bdd-refund"))
            .body(String.format("{\"amountMinor\":%d}", amount))
            .post("/payments/{id}/refund", world.payment().id().toString());
    world.lastResponse(response);
    if (response.statusCode() == 200) {
      world.payment(world.paymentFrom(response));
      world.recordTransactionsForPayment();
    }
  }

  @Then("the payment status is {word}")
  public void paymentStatus(String status) {
    assertThat(world.payment()).isNotNull();
    assertThat(world.payment().status()).isEqualTo(status);
  }

  @Then("the payment captured amount is {long} minor units")
  public void capturedAmount(long amount) {
    assertThat(world.payment().capturedMinor()).isEqualTo(amount);
  }

  @Then("the payment refunded amount is {long} minor units")
  public void refundedAmount(long amount) {
    assertThat(world.payment().refundedMinor()).isEqualTo(amount);
  }

  @Then("the {word} posted balance is {long} minor units")
  public void postedBalance(String role, long amount) {
    assertThat(world.balance(role).postedMinor()).isEqualTo(amount);
  }

  @Then("the {word} held balance is {long} minor units")
  public void heldBalance(String role, long amount) {
    assertThat(world.balance(role).heldMinor()).isEqualTo(amount);
  }

  @Then("the {word} available balance is {long} minor units")
  public void availableBalance(String role, long amount) {
    assertThat(world.balance(role).availableMinor()).isEqualTo(amount);
  }

  @Then("the request is rejected with code {word}")
  public void requestRejectedWithCode(String code) {
    assertThat(world.lastResponse()).isNotNull();
    assertThat(world.lastResponse().statusCode()).isBetween(400, 499);
    assertThat(world.lastResponse().jsonPath().getString("code")).isEqualTo(code);
  }
}
