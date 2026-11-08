package com.stanley.ledger.ledger;

import java.util.UUID;

/** Change to one account caused by one business operation. */
public record Delta(UUID accountId, long postedDelta, long heldDelta) {
  public boolean isZero() {
    return postedDelta == 0 && heldDelta == 0;
  }
}
