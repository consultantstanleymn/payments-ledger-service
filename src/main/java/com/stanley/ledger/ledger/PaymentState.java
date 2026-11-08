package com.stanley.ledger.ledger;

import java.util.UUID;

public record PaymentState(
    UUID id,
    UUID payerId,
    UUID payeeId,
    String currency,
    long authorizedMinor,
    long capturedMinor,
    long refundedMinor,
    Status status) {

  public enum Status {
    AUTHORIZED,
    CAPTURED,
    VOIDED
  }
}
