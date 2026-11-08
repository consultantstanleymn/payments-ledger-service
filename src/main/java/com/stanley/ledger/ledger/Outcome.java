package com.stanley.ledger.ledger;

import java.util.List;

/**
 * Result of a rule check: journal entries to write (empty for holds) and account deltas. The
 * constructor enforces the zero-sum rule in code. A database trigger enforces it again.
 */
public record Outcome(List<Entry> entries, List<Delta> deltas) {
  public Outcome {
    long sum = 0;
    for (Entry e : entries) {
      sum = Math.addExact(sum, e.amountMinor());
    }
    if (sum != 0) {
      throw new IllegalStateException("journal entries must sum to zero, got " + sum);
    }
  }
}
