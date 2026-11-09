package com.stanley.ledger.ops;

import com.stanley.ledger.ledger.LedgerException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Read-only queries behind the operations console. The controller calls this service. */
@Service
public class OpsQueryService {
  private static final String ACCOUNTS_SORT = "accounts";
  private static final String ENTRIES_SORT = "entries";
  private static final String PAYMENTS_SORT = "payments";
  private static final Set<String> PAYMENT_STATUSES = Set.of("AUTHORIZED", "CAPTURED", "VOIDED");

  public record AccountView(
      UUID id,
      String name,
      String currency,
      long postedMinor,
      long heldMinor,
      long availableMinor,
      long floorMinor,
      Instant createdAt) {}

  public record EntryView(
      long entryId,
      UUID txnId,
      String kind,
      String reference,
      long amountMinor,
      Instant createdAt) {}

  public record PaymentView(
      UUID id,
      UUID payerId,
      UUID payeeId,
      String currency,
      long authorizedMinor,
      long capturedMinor,
      long refundedMinor,
      String status,
      String externalRef,
      Instant createdAt) {}

  private final JdbcClient jdbc;

  public OpsQueryService(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public PageResult<AccountView> accounts(String currency, String q, int limit, String cursor) {
    int pageSize = pageSize(limit);
    Cursor decoded = decode(cursor, ACCOUNTS_SORT);
    Instant afterKey = decoded == null ? null : instantKey(decoded);
    UUID afterId = decoded == null ? null : uuidId(decoded);
    String namePrefix = likePrefix(q);
    String currencyFilter = blankToNull(currency);

    StringBuilder sql =
        new StringBuilder(
            "SELECT id, name, currency, posted_minor, held_minor, floor_minor, created_at"
                + " FROM accounts");
    List<String> conditions = new ArrayList<>();
    if (currencyFilter != null) {
      conditions.add("currency = :currency");
    }
    if (namePrefix != null) {
      conditions.add("lower(name) LIKE lower(:prefix) ESCAPE '\\'");
    }
    if (decoded != null) {
      conditions.add("(created_at, id) < (CAST(:ck AS timestamptz), CAST(:cid AS uuid))");
    }
    appendWhere(sql, conditions);
    sql.append(" ORDER BY created_at DESC, id DESC LIMIT :lim");

    JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
    if (currencyFilter != null) {
      spec = spec.param("currency", currencyFilter);
    }
    if (namePrefix != null) {
      spec = spec.param("prefix", namePrefix);
    }
    if (decoded != null) {
      spec = spec.param("ck", afterKey.toString()).param("cid", afterId);
    }
    spec = spec.param("lim", pageSize + 1);

    List<AccountView> rows =
        spec.query(
                (rs, row) -> {
                  long posted = rs.getLong("posted_minor");
                  long held = rs.getLong("held_minor");
                  return new AccountView(
                      (UUID) rs.getObject("id"),
                      rs.getString("name"),
                      rs.getString("currency"),
                      posted,
                      held,
                      Math.subtractExact(posted, held),
                      rs.getLong("floor_minor"),
                      rs.getTimestamp("created_at").toInstant());
                })
            .list();
    return page(
        rows, pageSize, ACCOUNTS_SORT, a -> a.createdAt().toString(), a -> a.id().toString());
  }

  public PageResult<EntryView> entries(UUID accountId, int limit, String cursor) {
    int pageSize = pageSize(limit);
    Cursor decoded = decode(cursor, ENTRIES_SORT);
    Long afterId = decoded == null ? null : longKey(decoded);
    if (!accountExists(accountId)) {
      throw LedgerException.notFound("no account " + accountId);
    }

    StringBuilder sql =
        new StringBuilder(
            "SELECT e.id AS entry_id, e.txn_id, t.kind, t.reference, e.amount_minor, t.created_at"
                + " FROM journal_entries e JOIN journal_transactions t ON e.txn_id = t.id"
                + " WHERE e.account_id = :account");
    if (decoded != null) {
      sql.append(" AND e.id < :cid");
    }
    sql.append(" ORDER BY e.id DESC LIMIT :lim");

    JdbcClient.StatementSpec spec = jdbc.sql(sql.toString()).param("account", accountId);
    if (decoded != null) {
      spec = spec.param("cid", afterId);
    }
    spec = spec.param("lim", pageSize + 1);

    List<EntryView> rows =
        spec.query((rs, row) ->
                new EntryView(
                    rs.getLong("entry_id"),
                    (UUID) rs.getObject("txn_id"),
                    rs.getString("kind"),
                    rs.getString("reference"),
                    rs.getLong("amount_minor"),
                    rs.getTimestamp("created_at").toInstant()))
            .list();
    return page(
        rows,
        pageSize,
        ENTRIES_SORT,
        e -> Long.toString(e.entryId()),
        e -> Long.toString(e.entryId()));
  }

  public PageResult<PaymentView> payments(String status, int limit, String cursor) {
    int pageSize = pageSize(limit);
    Cursor decoded = decode(cursor, PAYMENTS_SORT);
    Instant afterKey = decoded == null ? null : instantKey(decoded);
    UUID afterId = decoded == null ? null : uuidId(decoded);
    String statusFilter = paymentStatus(status);

    StringBuilder sql =
        new StringBuilder(
            "SELECT id, payer_account_id, payee_account_id, currency, authorized_minor,"
                + " captured_minor, refunded_minor, status, external_ref, created_at FROM payments");
    List<String> conditions = new ArrayList<>();
    if (statusFilter != null) {
      conditions.add("status = :status");
    }
    if (decoded != null) {
      conditions.add("(created_at, id) < (CAST(:ck AS timestamptz), CAST(:cid AS uuid))");
    }
    appendWhere(sql, conditions);
    sql.append(" ORDER BY created_at DESC, id DESC LIMIT :lim");

    JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
    if (statusFilter != null) {
      spec = spec.param("status", statusFilter);
    }
    if (decoded != null) {
      spec = spec.param("ck", afterKey.toString()).param("cid", afterId);
    }
    spec = spec.param("lim", pageSize + 1);

    List<PaymentView> rows =
        spec.query((rs, row) ->
                new PaymentView(
                    (UUID) rs.getObject("id"),
                    (UUID) rs.getObject("payer_account_id"),
                    (UUID) rs.getObject("payee_account_id"),
                    rs.getString("currency"),
                    rs.getLong("authorized_minor"),
                    rs.getLong("captured_minor"),
                    rs.getLong("refunded_minor"),
                    rs.getString("status"),
                    rs.getString("external_ref"),
                    rs.getTimestamp("created_at").toInstant()))
            .list();
    return page(
        rows, pageSize, PAYMENTS_SORT, p -> p.createdAt().toString(), p -> p.id().toString());
  }

  private boolean accountExists(UUID accountId) {
    return jdbc.sql("SELECT 1 FROM accounts WHERE id = :id")
        .param("id", accountId)
        .query(Integer.class)
        .optional()
        .isPresent();
  }

  private static <T> PageResult<T> page(
      List<T> rows, int pageSize, String sort, Function<T, String> key, Function<T, String> id) {
    if (rows.size() <= pageSize) {
      return new PageResult<>(rows, null);
    }
    List<T> items = new ArrayList<>(rows.subList(0, pageSize));
    T last = items.get(items.size() - 1);
    return new PageResult<>(items, Cursor.encode(sort, key.apply(last), id.apply(last)));
  }

  private static void appendWhere(StringBuilder sql, List<String> conditions) {
    if (!conditions.isEmpty()) {
      sql.append(" WHERE ").append(String.join(" AND ", conditions));
    }
  }

  private static int pageSize(int limit) {
    if (limit < 1 || limit > 100) {
      throw new LedgerException(400, "INVALID_LIMIT", "limit must be between 1 and 100");
    }
    return limit;
  }

  private static Cursor decode(String cursor, String sort) {
    return cursor == null || cursor.isBlank() ? null : Cursor.decode(cursor, sort);
  }

  private static Instant instantKey(Cursor cursor) {
    try {
      return Instant.parse(cursor.key());
    } catch (DateTimeParseException e) {
      throw invalidCursor();
    }
  }

  private static UUID uuidId(Cursor cursor) {
    try {
      return UUID.fromString(cursor.id());
    } catch (IllegalArgumentException e) {
      throw invalidCursor();
    }
  }

  private static long longKey(Cursor cursor) {
    try {
      return Long.parseLong(cursor.key());
    } catch (NumberFormatException e) {
      throw invalidCursor();
    }
  }

  private static LedgerException invalidCursor() {
    return new LedgerException(400, "INVALID_CURSOR", "cursor is not valid for this sort");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String likePrefix(String q) {
    if (q == null || q.isBlank()) {
      return null;
    }
    String value = q.strip();
    if (value.length() > 64) {
      throw new LedgerException(400, "INVALID_QUERY", "q must be at most 64 characters");
    }
    StringBuilder escaped = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == '\\' || ch == '%' || ch == '_') {
        escaped.append('\\');
      }
      escaped.append(ch);
    }
    return escaped.append('%').toString();
  }

  private static String paymentStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    String value = status.strip();
    if (!PAYMENT_STATUSES.contains(value)) {
      throw new LedgerException(
          400, "INVALID_STATUS", "status must be AUTHORIZED, CAPTURED or VOIDED");
    }
    return value;
  }
}
