Feature: Transfers

  A transfer moves funds from one account to another.
  The sender must stay at or above its floor.

  @smoke
  Scenario: A transfer moves funds between accounts
    Given a bank account
    And a sender account funded with 5000 minor units and a floor of 0 minor units
    And a receiver account
    When the sender transfers 1500 minor units to the receiver
    Then the transfer is accepted
    And the sender posted balance is 3500 minor units
    And the receiver posted balance is 1500 minor units
    Then the journal is balanced

  @regression
  Scenario Outline: A transfer obeys the sender floor
    Given a bank account
    And a sender account funded with <funds> minor units and a floor of <floor> minor units
    And a receiver account
    When the sender transfers <amount> minor units to the receiver
    Then the transfer is <result>
    And the receiver posted balance is <receiver> minor units
    Then the journal is balanced

    Examples:
      | funds | floor | amount | result   | receiver |
      | 5000  | 0     | 1000   | accepted | 1000     |
      | 5000  | 0     | 5000   | accepted | 5000     |
      | 5000  | 0     | 5001   | rejected | 0        |
      | 5000  | -1000 | 5500   | accepted | 5500     |
      | 5000  | -1000 | 6001   | rejected | 0        |
