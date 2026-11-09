package com.stanley.ledger.qa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.qa.support.ApiClient;
import com.stanley.ledger.qa.support.LedgerDb;
import com.stanley.ledger.qa.support.TestData;
import com.stanley.ledger.qa.support.TestData.Account;
import com.stanley.ledger.qa.support.TestData.Payment;
import com.stanley.ledger.qa.support.TestData.Transfer;
import io.restassured.response.Response;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Positive payment and transfer routes seen from the outside. */
class PaymentsApiTest {
  private final ApiClient api = new ApiClient();
  private final TestData data = new TestData(api);
  private final LedgerDb db = new LedgerDb();

  private Response balance(UUID accountId) {
    return api.request().get("/accounts/{id}/balance", accountId.toString());
  }

  @Test
  void authorizeCaptureRefundLifecycle() {
    Account bank = data.account().floorMinor(-1_000_000L).create();
    Account payer = data.account().create();
    Account payee = data.account().create();
    data.transfer().from(bank).to(payer).amount(10_000).post();

    Payment authorized = data.payment().from(payer).to(payee).amount(1_000).authorize();
    assertThat(authorized.status()).isEqualTo("AUTHORIZED");
    assertThat(authorized.currency()).isEqualTo("USD");
    assertThat(authorized.authorizedMinor()).isEqualTo(1_000L);
    assertThat(authorized.capturedMinor()).isZero();
    assertThat(authorized.refundedMinor()).isZero();

    Response held = balance(payer.id());
    assertThat(held.jsonPath().getLong("postedMinor")).isEqualTo(10_000L);
    assertThat(held.jsonPath().getLong("heldMinor")).isEqualTo(1_000L);
    assertThat(held.jsonPath().getLong("availableMinor")).isEqualTo(9_000L);

    Payment captured = data.capture(authorized, 600);
    assertThat(captured.status()).isEqualTo("CAPTURED");
    assertThat(captured.authorizedMinor()).isEqualTo(1_000L);
    assertThat(captured.capturedMinor()).isEqualTo(600L);
    assertThat(captured.refundedMinor()).isZero();

    Response payerAfterCapture = balance(payer.id());
    assertThat(payerAfterCapture.jsonPath().getLong("postedMinor")).isEqualTo(9_400L);
    assertThat(payerAfterCapture.jsonPath().getLong("heldMinor")).isZero();
    assertThat(payerAfterCapture.jsonPath().getLong("availableMinor")).isEqualTo(9_400L);
    assertThat(balance(payee.id()).jsonPath().getLong("postedMinor")).isEqualTo(600L);

    assertThat(db.paymentStatus(captured.id())).isEqualTo("CAPTURED");
    List<UUID> capturedTransactions = db.transactionIdsForReference(captured.id());
    assertThat(capturedTransactions).isNotEmpty();
    assertThat(db.journalBalancedFor(capturedTransactions)).isTrue();
    assertThat(db.cachedBalanceMatchesJournal(payer.id())).isTrue();
    assertThat(db.cachedBalanceMatchesJournal(payee.id())).isTrue();

    Payment refunded = data.refund(captured, 200);
    assertThat(refunded.status()).isEqualTo("CAPTURED");
    assertThat(refunded.authorizedMinor()).isEqualTo(1_000L);
    assertThat(refunded.capturedMinor()).isEqualTo(600L);
    assertThat(refunded.refundedMinor()).isEqualTo(200L);

    Response payerAfterRefund = balance(payer.id());
    assertThat(payerAfterRefund.jsonPath().getLong("postedMinor")).isEqualTo(9_600L);
    assertThat(payerAfterRefund.jsonPath().getLong("heldMinor")).isZero();
    assertThat(balance(payee.id()).jsonPath().getLong("postedMinor")).isEqualTo(400L);

    assertThat(db.paymentStatus(refunded.id())).isEqualTo("CAPTURED");
    List<UUID> refundedTransactions = db.transactionIdsForReference(refunded.id());
    assertThat(refundedTransactions).hasSize(2);
    assertThat(db.journalBalancedFor(refundedTransactions)).isTrue();
  }

  @Test
  void transferMovesPostedBalance() {
    Account bank = data.account().floorMinor(-1_000_000L).create();
    Account from = data.account().create();
    Account to = data.account().create();
    data.transfer().from(bank).to(from).amount(5_000).post();

    Transfer moved = data.transfer().from(from).to(to).amount(1_250).post();

    Response fromBalance = balance(from.id());
    assertThat(fromBalance.jsonPath().getLong("postedMinor")).isEqualTo(3_750L);
    assertThat(fromBalance.jsonPath().getLong("heldMinor")).isZero();

    Response toBalance = balance(to.id());
    assertThat(toBalance.jsonPath().getLong("postedMinor")).isEqualTo(1_250L);
    assertThat(toBalance.jsonPath().getLong("heldMinor")).isZero();

    assertThat(db.journalBalancedFor(List.of(moved.transactionId()))).isTrue();
    assertThat(db.cachedBalanceMatchesJournal(from.id())).isTrue();
    assertThat(db.cachedBalanceMatchesJournal(to.id())).isTrue();
  }

  @Test
  void voidReleasesHold() {
    Account bank = data.account().floorMinor(-1_000_000L).create();
    Account payer = data.account().create();
    Account payee = data.account().create();
    data.transfer().from(bank).to(payer).amount(2_000).post();

    Payment authorized = data.payment().from(payer).to(payee).amount(700).authorize();
    Response held = balance(payer.id());
    assertThat(held.jsonPath().getLong("postedMinor")).isEqualTo(2_000L);
    assertThat(held.jsonPath().getLong("heldMinor")).isEqualTo(700L);
    assertThat(held.jsonPath().getLong("availableMinor")).isEqualTo(1_300L);

    Payment voided = data.voidPayment(authorized);
    assertThat(voided.status()).isEqualTo("VOIDED");
    assertThat(voided.authorizedMinor()).isEqualTo(700L);
    assertThat(voided.capturedMinor()).isZero();
    assertThat(voided.refundedMinor()).isZero();

    Response released = balance(payer.id());
    assertThat(released.jsonPath().getLong("postedMinor")).isEqualTo(2_000L);
    assertThat(released.jsonPath().getLong("heldMinor")).isZero();
    assertThat(released.jsonPath().getLong("availableMinor")).isEqualTo(2_000L);
    assertThat(db.paymentStatus(voided.id())).isEqualTo("VOIDED");
  }
}
