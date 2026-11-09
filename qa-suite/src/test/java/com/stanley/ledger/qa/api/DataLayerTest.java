package com.stanley.ledger.qa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.qa.support.LedgerDb;
import com.stanley.ledger.qa.support.RunId;
import com.stanley.ledger.qa.support.TestData;
import com.stanley.ledger.qa.support.TestData.Account;
import com.stanley.ledger.qa.support.TestData.Payment;
import com.stanley.ledger.qa.support.TestData.Transfer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Checks the data layer from both sides: through the API and directly against the database. */
class DataLayerTest {
  private final LedgerDb db = new LedgerDb();

  @Test
  void createdAccountsAreTaggedWithRunId() {
    TestData data = new TestData();
    Account usd = data.account().currency("USD").create();
    Account eur = data.account().currency("EUR").floorMinor(-500).create();

    String prefix = "qa-" + RunId.value() + "-";
    assertThat(usd.name()).startsWith(prefix);
    assertThat(eur.name()).startsWith(prefix);

    List<UUID> runAccounts = db.accountsForRun(RunId.value());
    assertThat(runAccounts).contains(usd.id(), eur.id());
    assertThat(runAccounts).isNotEmpty();
  }

  @Test
  void authorizeWritesBalancedJournal() {
    TestData data = new TestData();
    Account bank = data.account().floorMinor(-1_000_000_000L).create();
    Account payer = data.account().create();
    Account payee = data.account().create();

    Transfer funding = data.transfer().from(bank).to(payer).amount(20_000).post();
    Payment authorized = data.payment().from(payer).to(payee).amount(5_000).authorize();

    assertThat(db.paymentStatus(authorized.id())).isEqualTo("AUTHORIZED");
    assertThat(db.journalBalancedFor(List.of(funding.transactionId()))).isTrue();

    Payment captured = data.capture(authorized);
    assertThat(captured.status()).isEqualTo("CAPTURED");

    List<UUID> paymentTransactions = db.transactionIdsForReference(authorized.id());
    assertThat(paymentTransactions).isNotEmpty();
    assertThat(db.journalBalancedFor(paymentTransactions)).isTrue();
  }

  @Test
  void cachedBalanceEqualsJournalSum() {
    TestData data = new TestData();
    Account bank = data.account().floorMinor(-1_000_000_000L).create();
    Account payer = data.account().create();
    Account payee = data.account().create();

    data.transfer().from(bank).to(payer).amount(10_000).post();
    Payment payment = data.payment().from(payer).to(payee).amount(1_200).authorize();
    data.capture(payment);

    assertThat(db.cachedBalanceMatchesJournal(bank.id())).isTrue();
    assertThat(db.cachedBalanceMatchesJournal(payer.id())).isTrue();
    assertThat(db.cachedBalanceMatchesJournal(payee.id())).isTrue();

    long posted = db.entriesFor(payer.id()).stream()
        .mapToLong(LedgerDb.JournalEntry::amountMinor)
        .sum();
    assertThat(posted).isEqualTo(10_000L - 1_200L);
  }

  @Test
  void dbConnectionIsReadOnly() {
    assertThat(db.isReadOnly()).isTrue();
    assertThat(db.writeIsRejected()).isTrue();
  }
}
