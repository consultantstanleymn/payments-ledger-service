package com.stanley.ledger.ledger;

import java.util.UUID;

/** Balance view of one account. All amounts are integers in minor units (cents). */
public record AccountState(
    UUID id, String currency, long floorMinor, long postedMinor, long heldMinor) {

  public long availableMinor() {
    return Math.subtractExact(postedMinor, heldMinor);
  }

  public AccountState apply(Delta d) {
    return new AccountState(
        id,
        currency,
        floorMinor,
        Math.addExact(postedMinor, d.postedDelta()),
        Math.addExact(heldMinor, d.heldDelta()));
  }
}
