package com.stanley.ledger.qa.support;

import java.util.UUID;

/** One identifier per test JVM. Every test names its own data with it. */
public final class RunId {
  private static final String VALUE = UUID.randomUUID().toString();

  private RunId() {}

  public static String value() {
    return VALUE;
  }

  public static String accountName(int index) {
    return "qa-" + VALUE + "-" + index;
  }

  public static String idempotencyKey(String purpose) {
    return VALUE + ":" + purpose;
  }
}
