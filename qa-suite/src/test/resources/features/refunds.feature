Feature: Refunds

  A refund returns captured funds from the payee to the payer.
  The total of all refunds never exceeds the captured amount.

  Background:
    Given a bank account
    And a payer account
    And a payee account
    And the payer holds 10000 minor units
    And the payer authorizes a payment of 1000 minor units to the payee
    And the payment is captured in full

  @smoke
  Scenario: A partial refund returns part of the captured amount
    When the payment is refunded for 200 minor units
    Then the payment refunded amount is 200 minor units
    And the payer posted balance is 9200 minor units
    And the payee posted balance is 800 minor units
    Then the journal is balanced

  @regression
  Scenario: A second refund takes the total up to the captured amount
    When the payment is refunded for 400 minor units
    And the payment is refunded for 600 minor units
    Then the payment refunded amount is 1000 minor units
    And the payer posted balance is 10000 minor units
    And the payee posted balance is 0 minor units
    Then the journal is balanced

  @negative
  Scenario: A refund over the captured amount is rejected
    When the payment is refunded for 600 minor units
    And the payment is refunded for 500 minor units
    Then the request is rejected with code INVALID_REQUEST
    And the payment refunded amount is 600 minor units
    And the payer posted balance is 9600 minor units
    And the payee posted balance is 400 minor units
    Then the journal is balanced
