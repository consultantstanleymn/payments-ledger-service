package com.stanley.ledger.idem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a write once per (client, Idempotency-Key). The key row and the business writes share one
 * transaction. A second request with the same key waits on the unique index, then reads the stored
 * response. A failed request rolls back, key row included, so the client can retry.
 */
@Component
public class IdempotentExecutor {

  public record Stored(int status, String body, boolean replay) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;

  public IdempotentExecutor(JdbcClient jdbc, TransactionTemplate tx) {
    this.jdbc = jdbc;
    this.tx = tx;
  }

  public static String hash(String method, String path, String body) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update((method + " " + path + "\n" + body).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(md.digest());
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public Stored run(String clientId, String key, String requestHash, Supplier<Stored> action) {
    return tx.execute(status -> {
      int inserted =
          jdbc.sql(
                  "INSERT INTO idempotency_keys (client_id, idem_key, request_hash)"
                      + " VALUES (:c, :k, :h) ON CONFLICT DO NOTHING")
              .param("c", clientId)
              .param("k", key)
              .param("h", requestHash)
              .update();
      if (inserted == 0) {
        var row =
            jdbc.sql(
                    "SELECT request_hash, status, response FROM idempotency_keys"
                        + " WHERE client_id = :c AND idem_key = :k")
                .param("c", clientId)
                .param("k", key)
                .query((rs, n) ->
                    new String[] {
                      rs.getString("request_hash"), rs.getString("status"), rs.getString("response")
                    })
                .single();
        if (!row[0].equals(requestHash)) {
          return new Stored(
              422,
              "{\"code\":\"IDEMPOTENCY_KEY_REUSED\",\"message\":\"key used with a different"
                  + " request\"}",
              false);
        }
        return new Stored(Integer.parseInt(row[1]), row[2], true);
      }
      Stored result = action.get();
      jdbc.sql(
              "UPDATE idempotency_keys SET status = :s, response = :r"
                  + " WHERE client_id = :c AND idem_key = :k")
          .param("s", result.status())
          .param("r", result.body())
          .param("c", clientId)
          .param("k", key)
          .update();
      return result;
    });
  }
}
