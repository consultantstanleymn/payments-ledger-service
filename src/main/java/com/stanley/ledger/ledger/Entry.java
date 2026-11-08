package com.stanley.ledger.ledger;

import java.util.UUID;

/** One signed journal line. Positive credits the account, negative debits it. */
public record Entry(UUID accountId, long amountMinor) {}
