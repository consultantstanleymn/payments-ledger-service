package com.stanley.ledger.ops;

/** Opaque keyset paging cursor. The wire form is unpadded base64url of a small JSON object. */
public record Cursor(String sort, String key, String id) {

  public static String encode(String sort, String key, String id) {
    throw new UnsupportedOperationException("cursor encoding is not implemented");
  }

  public static Cursor decode(String encoded, String expectedSort) {
    throw new UnsupportedOperationException("cursor decoding is not implemented");
  }
}
