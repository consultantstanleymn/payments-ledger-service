package com.stanley.ledger.projection;

/**
 * The event payload is malformed. Retrying cannot help, so the event goes to the dead-letter topic.
 */
public class PoisonEventException extends RuntimeException {
  public PoisonEventException(String message, Throwable cause) {
    super(message, cause);
  }
}
