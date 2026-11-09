package com.stanley.ledger.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stanley.ledger.ledger.LedgerException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class CursorTest {
  private static final String KEY = "2026-01-02T03:04:05Z";
  private static final String ID = "00000000-0000-0000-0000-000000000001";

  @Test
  void roundTrip() {
    String encoded = Cursor.encode("accounts", KEY, ID);

    Cursor decoded = Cursor.decode(encoded, "accounts");

    assertThat(decoded.sort()).isEqualTo("accounts");
    assertThat(decoded.key()).isEqualTo(KEY);
    assertThat(decoded.id()).isEqualTo(ID);
  }

  @Test
  void tamperedCursorRejected() {
    String encoded = payload("{\"k\":\"" + KEY + "\",\"id\":\"\",\"s\":\"accounts\"}");

    assertBadCursor(() -> Cursor.decode(encoded, "accounts"));
  }

  @Test
  void cursorFromOtherSortRejected() {
    String encoded = Cursor.encode("payments", KEY, ID);

    assertBadCursor(() -> Cursor.decode(encoded, "accounts"));
  }

  @Test
  void garbageRejected() {
    assertBadCursor(() -> Cursor.decode("%%%", "accounts"));
    assertBadCursor(() -> Cursor.decode("", "accounts"));
  }

  private static String payload(String json) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  private static void assertBadCursor(ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(LedgerException.class)
        .satisfies(e -> assertThat(((LedgerException) e).httpStatus()).isEqualTo(400));
  }
}
