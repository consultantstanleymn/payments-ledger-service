package com.stanley.ledger.ledger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** In-memory ledger that applies the same Rules as the service. Used by property tests. */
final class LedgerModel {
  final Map<UUID, AccountState> accounts = new HashMap<>();
  final Map<UUID, PaymentState> payments = new HashMap<>();
  final List<List<Entry>> journal = new ArrayList<>();
  final List<UUID> accountIds = new ArrayList<>();
  final List<UUID> paymentIds = new ArrayList<>();
  int rejected;

  UUID addAccount(long floor, long posted) {
    UUID id = UUID.randomUUID();
    accounts.put(id, new AccountState(id, "USD", floor, posted, 0));
    accountIds.add(id);
    return id;
  }

  private void apply(Outcome o) {
    for (Delta d : o.deltas()) {
      accounts.put(d.accountId(), accounts.get(d.accountId()).apply(d));
    }
    if (!o.entries().isEmpty()) {
      journal.add(o.entries());
    }
  }

  void transfer(int a, int b, long amount) {
    try {
      apply(
          Rules.transfer(accounts.get(accountIds.get(a)), accounts.get(accountIds.get(b)), amount));
    } catch (LedgerException e) {
      rejected++;
    }
  }

  void authorize(int a, int b, long amount) {
    try {
      UUID payer = accountIds.get(a);
      UUID payee = accountIds.get(b);
      apply(Rules.authorize(accounts.get(payer), accounts.get(payee), amount));
      UUID id = UUID.randomUUID();
      payments.put(
          id,
          new PaymentState(id, payer, payee, "USD", amount, 0, 0, PaymentState.Status.AUTHORIZED));
      paymentIds.add(id);
    } catch (LedgerException e) {
      rejected++;
    }
  }

  private PaymentState payment(int pick) {
    return paymentIds.isEmpty() ? null : payments.get(paymentIds.get(pick % paymentIds.size()));
  }

  void voidPayment(int pick) {
    PaymentState p = payment(pick);
    if (p == null) return;
    try {
      apply(Rules.voidPayment(p));
      payments.put(
          p.id(),
          new PaymentState(
              p.id(),
              p.payerId(),
              p.payeeId(),
              "USD",
              p.authorizedMinor(),
              0,
              0,
              PaymentState.Status.VOIDED));
    } catch (LedgerException e) {
      rejected++;
    }
  }

  void capture(int pick, long amount) {
    PaymentState p = payment(pick);
    if (p == null) return;
    try {
      apply(Rules.capture(p, amount));
      payments.put(
          p.id(),
          new PaymentState(
              p.id(),
              p.payerId(),
              p.payeeId(),
              "USD",
              p.authorizedMinor(),
              amount,
              0,
              PaymentState.Status.CAPTURED));
    } catch (LedgerException e) {
      rejected++;
    }
  }

  void refund(int pick, long amount) {
    PaymentState p = payment(pick);
    if (p == null) return;
    try {
      apply(Rules.refund(p, accounts.get(p.payeeId()), amount));
      payments.put(
          p.id(),
          new PaymentState(
              p.id(),
              p.payerId(),
              p.payeeId(),
              "USD",
              p.authorizedMinor(),
              p.capturedMinor(),
              p.refundedMinor() + amount,
              p.status()));
    } catch (LedgerException e) {
      rejected++;
    }
  }

  /** Checks every invariant. Returns an error text, or null when all hold. */
  String violation() {
    long totalPosted = 0;
    Map<UUID, Long> fromJournal = new HashMap<>();
    for (List<Entry> txn : journal) {
      long sum = 0;
      for (Entry e : txn) {
        sum += e.amountMinor();
        fromJournal.merge(e.accountId(), e.amountMinor(), Long::sum);
      }
      if (sum != 0) return "journal transaction does not sum to zero: " + sum;
    }
    Map<UUID, Long> held = new HashMap<>();
    for (PaymentState p : payments.values()) {
      if (p.status() == PaymentState.Status.AUTHORIZED)
        held.merge(p.payerId(), p.authorizedMinor(), Long::sum);
    }
    for (AccountState a : accounts.values()) {
      totalPosted += a.postedMinor();
      if (a.heldMinor() < 0) return "negative hold";
      if (a.postedMinor() - a.heldMinor() < a.floorMinor()) return "account below floor: " + a;
      long initial = initialPosted.getOrDefault(a.id(), 0L);
      if (a.postedMinor() != initial + fromJournal.getOrDefault(a.id(), 0L))
        return "cache differs from journal";
      if (a.heldMinor() != held.getOrDefault(a.id(), 0L))
        return "held differs from active payments";
    }
    return totalPosted == initialTotal ? null : "money created or destroyed";
  }

  final Map<UUID, Long> initialPosted = new HashMap<>();
  long initialTotal;

  void seal() {
    for (AccountState a : accounts.values()) {
      initialPosted.put(a.id(), a.postedMinor());
      initialTotal += a.postedMinor();
    }
  }
}
