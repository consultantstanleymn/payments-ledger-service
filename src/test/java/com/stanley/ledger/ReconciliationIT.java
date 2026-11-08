package com.stanley.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.ledger.PaymentState;
import com.stanley.ledger.recon.ReconciliationService;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReconciliationIT extends IntegrationBase {
  @Autowired ReconciliationService recon;

  @Test
  void fixtureWithOneMissingOneMismatchedAndNinetyEightMatched() {
    List<UUID> ids = fundedAccounts(2, 10_000_000);
    List<PaymentState> captured = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      PaymentState p = ledger.authorize(ids.get(1), ids.get(2), 1000 + i);
      captured.add(ledger.capture(p.id(), null));
    }
    StringBuilder csv = new StringBuilder("payment_id,amount_minor\n");
    for (int i = 0; i < 100; i++) {
      PaymentState p = captured.get(i);
      if (i == 10) continue; // missing from the settlement file
      long amount = i == 20 ? p.capturedMinor() + 1 : p.capturedMinor(); // one amount differs
      csv.append(p.id()).append(',').append(amount).append('\n');
    }
    var r = recon.reconcile(new StringReader(csv.toString()));
    assertThat(r.matched()).isEqualTo(98);
    assertThat(r.missing()).isEqualTo(1);
    assertThat(r.mismatched()).isEqualTo(1);
    assertThat(r.unexpected()).isZero();
    assertThat(recon.get(r.id()).matched()).isEqualTo(98);
    assertThat(r.details()).contains(captured.get(10).id().toString());
  }

  @Test
  void rowNotInTheLedgerIsReportedAsUnexpected() {
    var r =
        recon.reconcile(new StringReader("payment_id,amount_minor\n" + UUID.randomUUID() + ",5\n"));
    assertThat(r.unexpected()).isEqualTo(1);
  }
}
