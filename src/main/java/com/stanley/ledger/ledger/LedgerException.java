package com.stanley.ledger.ledger;

/** A business rule failure. Carries the HTTP status that the API maps it to. */
public class LedgerException extends RuntimeException {
  private final int httpStatus;
  private final String code;

  public LedgerException(int httpStatus, String code, String message) {
    super(message);
    this.httpStatus = httpStatus;
    this.code = code;
  }

  public int httpStatus() {
    return httpStatus;
  }

  public String code() {
    return code;
  }

  public static LedgerException insufficient(String message) {
    return new LedgerException(409, "INSUFFICIENT_FUNDS", message);
  }

  public static LedgerException state(String message) {
    return new LedgerException(409, "INVALID_STATE", message);
  }

  public static LedgerException invalid(String message) {
    return new LedgerException(422, "INVALID_REQUEST", message);
  }

  public static LedgerException notFound(String message) {
    return new LedgerException(404, "NOT_FOUND", message);
  }
}
