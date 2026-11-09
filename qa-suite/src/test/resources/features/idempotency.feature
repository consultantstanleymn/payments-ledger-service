Feature: Idempotent writes

  A client can safely retry a write with the same idempotency key.
  The same key with a different request is rejected.

  Background:
    Given a bank account
    And a payer account
    And a payee account
    And the payer holds 5000 minor units

  @smoke
  Scenario: The same request with the same key creates one payment
    When the payer authorizes a payment of 1000 minor units to the payee with idempotency key "qa-idem-same" and external reference "qa-ref-same"
    And the payer repeats the last authorization request
    Then both responses hold the same payment identifier
    And the second response is a replay
    Then the journal is balanced

  @negative
  Scenario: The same key with another amount is rejected
    When the payer authorizes a payment of 1000 minor units to the payee with idempotency key "qa-idem-diff"
    And the payer authorizes a payment of 2000 minor units to the payee with idempotency key "qa-idem-diff"
    Then the request is rejected with code IDEMPOTENCY_KEY_REUSED
    Then the journal is balanced
