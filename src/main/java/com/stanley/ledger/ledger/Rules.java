package com.stanley.ledger.ledger;

import java.util.List;
import java.util.UUID;

/**
 * Pure business rules. No database, no clock, no random. The service loads state under row locks,
 * calls a rule, and stores the Outcome. Property tests call the same rules on an in-memory model.
 */
public final class Rules {
  private Rules() {}

  private static void positive(long amount) {
    if (amount <= 0) {
      throw LedgerException.invalid("amount must be a positive integer in minor units");
    }
  }

  private static void sameCurrency(String a, String b) {
    if (!a.equals(b)) {
      throw LedgerException.invalid("currency mismatch: " + a + " vs " + b);
    }
  }

  private static void mustSpend(AccountState from, long amount) {
    if (from.availableMinor() - amount < from.floorMinor()) {
      throw LedgerException.insufficient(
          "account "
              + from.id()
              + " available "
              + from.availableMinor()
              + " below amount "
              + amount);
    }
  }

  private static Outcome move(UUID from, UUID to, long amount) {
    return new Outcome(
        List.of(new Entry(from, -amount), new Entry(to, amount)),
        List.of(new Delta(from, -amount, 0), new Delta(to, amount, 0)));
  }

  public static Outcome transfer(AccountState from, AccountState to, long amount) {
    positive(amount);
    if (from.id().equals(to.id())) {
      throw LedgerException.invalid("cannot transfer to the same account");
    }
    sameCurrency(from.currency(), to.currency());
    mustSpend(from, amount);
    return move(from.id(), to.id(), amount);
  }

  /** A hold reserves funds. It moves no money, so it writes no journal entries. */
  public static Outcome authorize(AccountState payer, AccountState payee, long amount) {
    positive(amount);
    if (payer.id().equals(payee.id())) {
      throw LedgerException.invalid("payer and payee must differ");
    }
    sameCurrency(payer.currency(), payee.currency());
    mustSpend(payer, amount);
    return new Outcome(List.of(), List.of(new Delta(payer.id(), 0, amount)));
  }

  public static Outcome voidPayment(PaymentState p) {
    if (p.status() != PaymentState.Status.AUTHORIZED) {
      throw LedgerException.state("only an authorized payment can be voided");
    }
    return new Outcome(List.of(), List.of(new Delta(p.payerId(), 0, -p.authorizedMinor())));
  }

  /** Capture settles up to the authorized amount and releases the whole hold. */
  public static Outcome capture(PaymentState p, long amount) {
    if (p.status() != PaymentState.Status.AUTHORIZED) {
      throw LedgerException.state("only an authorized payment can be captured");
    }
    positive(amount);
    if (amount > p.authorizedMinor()) {
      throw LedgerException.invalid("capture amount exceeds authorized amount");
    }
    return new Outcome(
        List.of(new Entry(p.payerId(), -amount), new Entry(p.payeeId(), amount)),
        List.of(
            new Delta(p.payerId(), -amount, -p.authorizedMinor()),
            new Delta(p.payeeId(), amount, 0)));
  }

  public static Outcome refund(PaymentState p, AccountState payee, long amount) {
    if (p.status() != PaymentState.Status.CAPTURED) {
      throw LedgerException.state("only a captured payment can be refunded");
    }
    positive(amount);
    if (amount > p.capturedMinor() - p.refundedMinor()) {
      throw LedgerException.invalid("refund amount exceeds captured amount not yet refunded");
    }
    mustSpend(payee, amount);
    return move(p.payeeId(), p.payerId(), amount);
  }
}
