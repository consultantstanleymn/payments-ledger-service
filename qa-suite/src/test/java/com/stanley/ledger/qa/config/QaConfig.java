package com.stanley.ledger.qa.config;

/** Target system settings. Every value comes from the environment with a local default. */
public final class QaConfig {
  private QaConfig() {}

  public static String ledgerUrl() {
    return env("LEDGER_URL", "http://127.0.0.1:18080");
  }

  public static String apiKey() {
    return env("LEDGER_API_KEY", "dev-key-local-only");
  }

  public static String adminKey() {
    return env("LEDGER_ADMIN_KEY", "");
  }

  public static String dbUrl() {
    return env("LEDGER_DB_URL", "jdbc:postgresql://127.0.0.1:18432/ledger");
  }

  public static String dbUser() {
    return env("LEDGER_DB_USER", "ledger");
  }

  public static String dbPassword() {
    return env("LEDGER_DB_PASSWORD", "ledger");
  }

  public static String seleniumUrl() {
    return env("SELENIUM_URL", "http://127.0.0.1:26744");
  }

  public static String consoleUrl() {
    return env("CONSOLE_URL", "http://127.0.0.1:18080/console/");
  }

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value;
  }
}
