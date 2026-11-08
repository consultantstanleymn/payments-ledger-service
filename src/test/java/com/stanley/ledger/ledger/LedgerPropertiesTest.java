package com.stanley.ledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.*;

class LedgerPropertiesTest {

  /** kind: 0 transfer, 1 authorize, 2 capture, 3 void, 4 refund. */
  record Op(int kind, int a, int b, long amount) {}

  @Provide
  Arbitrary<List<Op>> sequences() {
    Arbitrary<Op> op =
        Combinators.combine(
                Arbitraries.integers().between(0, 4),
                Arbitraries.integers().between(0, 5),
                Arbitraries.integers().between(0, 5),
                Arbitraries.longs().between(1, 50_000))
            .as(Op::new);
    return op.list().ofMinSize(1).ofMaxSize(80);
  }

  @Property(tries = 1000)
  void everyTransactionBalancesAndNoAccountBreaksItsFloor(@ForAll("sequences") List<Op> ops) {
    LedgerModel m = new LedgerModel();
    // account 0 is a funding source that may go far negative. The rest have floor 0.
    m.addAccount(-10_000_000, 0);
    for (int i = 1; i < 6; i++) m.addAccount(0, 20_000);
    m.seal();
    for (Op op : ops) {
      switch (op.kind()) {
        case 0 -> m.transfer(op.a(), op.b(), op.amount());
        case 1 -> m.authorize(op.a(), op.b(), op.amount());
        case 2 -> m.capture(op.a(), op.amount());
        case 3 -> m.voidPayment(op.a());
        default -> m.refund(op.a(), op.amount());
      }
      assertThat(m.violation()).isNull();
    }
  }

  @Property(tries = 200)
  void anOutcomeThatDoesNotBalanceIsRejected(
      @ForAll @net.jqwik.api.constraints.LongRange(min = 1, max = 1000) long x) {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            new Outcome(
                List.of(
                    new Entry(java.util.UUID.randomUUID(), -x),
                    new Entry(java.util.UUID.randomUUID(), x + 1)),
                List.of()));
  }
}
