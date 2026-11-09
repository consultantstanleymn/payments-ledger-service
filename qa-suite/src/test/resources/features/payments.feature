Feature: Payments

  A payment reserves funds when it is authorized.
  It settles the reserved funds when it is captured.
  A payment that is voided before capture releases the reservation.

  Background:
    Given a bank account
    And a payer account
    And a payee account

  @smoke
  Scenario: Authorize and capture a payment in full
    Given the payer holds 10000 minor units
    When the payer authorizes a payment of 1000 minor units to the payee
    Then the payment status is AUTHORIZED
    And the payer available balance is 9000 minor units
    When the payment is captured in full
    Then the payment status is CAPTURED
    And the payer posted balance is 9000 minor units
    And the payee posted balance is 1000 minor units
    Then the journal is balanced

  @regression
  Scenario: A partial capture settles only the captured amount
    Given the payer holds 10000 minor units
    When the payer authorizes a payment of 1000 minor units to the payee
    And the payment is captured for 600 minor units
    Then the payment status is CAPTURED
    And the payment captured amount is 600 minor units
    And the payer posted balance is 9400 minor units
    And the payee posted balance is 600 minor units
    Then the journal is balanced

  @smoke
  Scenario: A void before capture releases the hold
    Given the payer holds 2000 minor units
    When the payer authorizes a payment of 700 minor units to the payee
    Then the payer held balance is 700 minor units
    When the payment is voided
    Then the payment status is VOIDED
    And the payer posted balance is 2000 minor units
    And the payer held balance is 0 minor units
    Then the journal is balanced

  @regression
  Scenario: A capture releases the whole hold
    Given the payer holds 5000 minor units
    When the payer authorizes a payment of 1500 minor units to the payee
    And the payment is captured for 800 minor units
    Then the payer held balance is 0 minor units
    And the payer available balance is 4200 minor units
    Then the journal is balanced
