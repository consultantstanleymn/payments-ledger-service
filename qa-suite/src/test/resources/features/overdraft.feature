Feature: Overdraft protection

  An account can have a floor. A transfer that would take the account below the floor is rejected.
  A rejected transfer leaves both balances unchanged.

  @negative
  Scenario: A transfer below the floor is rejected and balances stay the same
    Given a bank account
    And a sender account funded with 2000 minor units and a floor of -500 minor units
    And a receiver account
    And the sender balance is recorded
    When the sender transfers 3000 minor units to the receiver
    Then the transfer is rejected
    And the sender balance is unchanged
    And the receiver posted balance is 0 minor units
    Then the journal is balanced
