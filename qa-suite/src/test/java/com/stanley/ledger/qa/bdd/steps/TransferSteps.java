package com.stanley.ledger.qa.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.qa.bdd.World;
import com.stanley.ledger.qa.support.TestData;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import java.util.UUID;

/** Transfer actions, the floor check, and the unchanged-balance check. */
public class TransferSteps {
  private final World world;

  public TransferSteps(World world) {
    this.world = world;
  }

  @Given("a sender account funded with {long} minor units and a floor of {long} minor units")
  public void aFundedSender(long funds, long floor) {
    TestData.Account sender = world.data().account().floorMinor(floor).create();
    world.account("sender", sender);
    TestData.Transfer funding =
        world.data().transfer().from(world.bank()).to(sender).amount(funds).post();
    world.addTransaction(funding.transactionId());
  }

  @When("the sender transfers {long} minor units to the receiver")
  public void transfer(long amount) {
    String body =
        String.format(
            "{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amountMinor\":%d}",
            world.account("sender").id(), world.account("receiver").id(), amount);
    Response response =
        world.api().withIdempotency(world.key("bdd-transfer")).body(body).post("/transfers");
    world.lastResponse(response);
    if (response.statusCode() == 201) {
      world.addTransaction(UUID.fromString(response.jsonPath().getString("transactionId")));
    }
  }

  @Then("the transfer is {word}")
  public void transferResult(String result) {
    if ("accepted".equals(result)) {
      assertAccepted();
    } else if ("rejected".equals(result)) {
      assertRejected();
    } else {
      throw new IllegalArgumentException("unknown expected transfer result: " + result);
    }
  }

  @Given("the sender balance is recorded")
  public void recordSenderBalance() {
    world.recordSenderBalance();
  }

  @Then("the sender balance is unchanged")
  public void senderBalanceUnchanged() {
    assertThat(world.balance("sender")).isEqualTo(world.recordedSenderBalance());
  }

  private void assertAccepted() {
    assertThat(world.lastResponse()).isNotNull();
    assertThat(world.lastResponse().statusCode())
        .withFailMessage("transfer was rejected: " + world.lastResponse().body().asString())
        .isEqualTo(201);
    assertThat(world.lastResponse().jsonPath().getString("transactionId")).isNotBlank();
  }

  private void assertRejected() {
    assertThat(world.lastResponse()).isNotNull();
    assertThat(world.lastResponse().statusCode())
        .withFailMessage("transfer was not rejected: " + world.lastResponse().body().asString())
        .isEqualTo(409);
    assertThat(world.lastResponse().jsonPath().getString("code")).isEqualTo("INSUFFICIENT_FUNDS");
  }
}
