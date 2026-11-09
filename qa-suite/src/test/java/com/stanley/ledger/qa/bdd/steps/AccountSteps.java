package com.stanley.ledger.qa.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.qa.bdd.World;
import com.stanley.ledger.qa.support.TestData;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

/** Account setup and the journal check that closes every scenario. */
public class AccountSteps {
  private final World world;

  public AccountSteps(World world) {
    this.world = world;
  }

  @Given("a bank account")
  public void aBankAccount() {
    world.bank();
  }

  @Given("a payer account")
  public void aPayerAccount() {
    world.account("payer", world.data().account().create());
  }

  @Given("a payee account")
  public void aPayeeAccount() {
    world.account("payee", world.data().account().create());
  }

  @Given("a receiver account")
  public void aReceiverAccount() {
    world.account("receiver", world.data().account().create());
  }

  @Given("the payer holds {long} minor units")
  public void thePayerHolds(long amount) {
    TestData.Account payer = world.account("payer");
    TestData.Transfer funding =
        world.data().transfer().from(world.bank()).to(payer).amount(amount).post();
    world.addTransaction(funding.transactionId());
  }

  @Then("the journal is balanced")
  public void theJournalIsBalanced() {
    assertThat(world.db().journalBalancedFor(world.transactionIds())).isTrue();
    for (String role : world.accountRoles()) {
      assertThat(world.db().cachedBalanceMatchesJournal(world.account(role).id())).isTrue();
    }
  }
}
