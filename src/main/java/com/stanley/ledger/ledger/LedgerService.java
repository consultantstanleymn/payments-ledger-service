package com.stanley.ledger.ledger;

import com.stanley.ledger.outbox.OutboxWriter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shell around the pure rules. Every public method is one database transaction. Rows are locked
 * with SELECT ... FOR UPDATE in a fixed order: payment first, then accounts sorted by id.
 */
@Service
public class LedgerService {
  private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("payments-ledger");

  private final JdbcClient jdbc;
  private final OutboxWriter outbox;

  public LedgerService(JdbcClient jdbc, OutboxWriter outbox) {
    this.jdbc = jdbc;
    this.outbox = outbox;
  }

  @Transactional
  public AccountState createAccount(String name, String currency, long floorMinor) {
    if (name == null || name.isBlank()) {
      throw LedgerException.invalid("name is required");
    }
    if (currency == null || !currency.matches("[A-Z]{3}")) {
      throw LedgerException.invalid("currency must be a 3 letter code such as USD");
    }
    if (floorMinor > 0) {
      throw LedgerException.invalid("floorMinor must be zero or negative");
    }
    UUID id = UUID.randomUUID();
    jdbc.sql("INSERT INTO accounts (id, name, currency, floor_minor) VALUES (:i, :n, :c, :f)")
        .param("i", id)
        .param("n", name)
        .param("c", currency)
        .param("f", floorMinor)
        .update();
    return new AccountState(id, currency, floorMinor, 0, 0);
  }

  @Transactional(readOnly = true)
  public AccountState balance(UUID id) {
    return jdbc.sql(
            "SELECT id, currency, floor_minor, posted_minor, held_minor FROM accounts WHERE id = :i")
        .param("i", id)
        .query(LedgerService::mapAccount)
        .optional()
        .orElseThrow(() -> LedgerException.notFound("account " + id));
  }

  @Transactional
  public UUID transfer(UUID from, UUID to, long amount) {
    List<AccountState> locked = lockAccounts(from, to);
    Outcome o = Rules.transfer(pick(locked, from), pick(locked, to), amount);
    return post("TRANSFER", "TRANSFER_POSTED", UUID.randomUUID(), o);
  }

  @Transactional
  public PaymentState authorize(UUID payer, UUID payee, long amount) {
    List<AccountState> locked = lockAccounts(payer, payee);
    Outcome o = Rules.authorize(pick(locked, payer), pick(locked, payee), amount);
    UUID paymentId = UUID.randomUUID();
    String currency = pick(locked, payer).currency();
    jdbc.sql(
            "INSERT INTO payments (id, payer_account_id, payee_account_id, currency,"
                + " authorized_minor, status) VALUES (:i, :a, :b, :c, :m, 'AUTHORIZED')")
        .param("i", paymentId)
        .param("a", payer)
        .param("b", payee)
        .param("c", currency)
        .param("m", amount)
        .update();
    post(null, "PAYMENT_AUTHORIZED", paymentId, o);
    return new PaymentState(
        paymentId, payer, payee, currency, amount, 0, 0, PaymentState.Status.AUTHORIZED);
  }

  @Transactional
  public PaymentState voidPayment(UUID paymentId) {
    PaymentState p = lockPayment(paymentId);
    Outcome o = Rules.voidPayment(p);
    lockAccounts(p.payerId(), p.payeeId());
    post(null, "PAYMENT_VOIDED", paymentId, o);
    jdbc.sql("UPDATE payments SET status = 'VOIDED' WHERE id = :i").param("i", paymentId).update();
    return lockPayment(paymentId);
  }

  @Transactional
  public PaymentState capture(UUID paymentId, Long amountOrNull) {
    PaymentState p = lockPayment(paymentId);
    long amount = amountOrNull == null ? p.authorizedMinor() : amountOrNull;
    Outcome o = Rules.capture(p, amount);
    lockAccounts(p.payerId(), p.payeeId());
    post("CAPTURE", "PAYMENT_CAPTURED", paymentId, o);
    jdbc.sql("UPDATE payments SET status = 'CAPTURED', captured_minor = :m WHERE id = :i")
        .param("m", amount)
        .param("i", paymentId)
        .update();
    return lockPayment(paymentId);
  }

  @Transactional
  public PaymentState refund(UUID paymentId, long amount) {
    PaymentState p = lockPayment(paymentId);
    List<AccountState> locked = lockAccounts(p.payerId(), p.payeeId());
    Outcome o = Rules.refund(p, pick(locked, p.payeeId()), amount);
    post("REFUND", "PAYMENT_REFUNDED", paymentId, o);
    jdbc.sql("UPDATE payments SET refunded_minor = refunded_minor + :m WHERE id = :i")
        .param("m", amount)
        .param("i", paymentId)
        .update();
    return lockPayment(paymentId);
  }

  @Transactional(readOnly = true)
  public PaymentState payment(UUID id) {
    return jdbc.sql(PAYMENT_SQL)
        .param("i", id)
        .query(LedgerService::mapPayment)
        .optional()
        .orElseThrow(() -> LedgerException.notFound("payment " + id));
  }

  /** Writes journal rows (when entries exist), updates cached balances, writes outbox rows. */
  private UUID post(String journalKind, String eventType, UUID reference, Outcome o) {
    Span span = TRACER.spanBuilder("ledger.post " + eventType).startSpan();
    try (Scope ignored = span.makeCurrent()) {
      UUID txnId = null;
      if (!o.entries().isEmpty()) {
        txnId = UUID.randomUUID();
        jdbc.sql("INSERT INTO journal_transactions (id, kind, reference) VALUES (:i, :k, :r)")
            .param("i", txnId)
            .param("k", journalKind)
            .param("r", reference.toString())
            .update();
        for (Entry e : o.entries()) {
          jdbc.sql(
                  "INSERT INTO journal_entries (txn_id, account_id, amount_minor)"
                      + " VALUES (:t, :a, :m)")
              .param("t", txnId)
              .param("a", e.accountId())
              .param("m", e.amountMinor())
              .update();
        }
      }
      for (Delta d : o.deltas()) {
        jdbc.sql(
                "UPDATE accounts SET posted_minor = posted_minor + :p,"
                    + " held_minor = held_minor + :h WHERE id = :i")
            .param("p", d.postedDelta())
            .param("h", d.heldDelta())
            .param("i", d.accountId())
            .update();
        outbox.write(eventType, reference, d);
      }
      return txnId == null ? reference : txnId;
    } finally {
      span.end();
    }
  }

  private List<AccountState> lockAccounts(UUID a, UUID b) {
    List<AccountState> rows =
        jdbc.sql(
                "SELECT id, currency, floor_minor, posted_minor, held_minor FROM accounts"
                    + " WHERE id IN (:a, :b) ORDER BY id FOR UPDATE")
            .param("a", a)
            .param("b", b)
            .query(LedgerService::mapAccount)
            .list();
    int expected = a.equals(b) ? 1 : 2;
    if (rows.size() != expected) {
      throw LedgerException.notFound("account not found");
    }
    return new ArrayList<>(rows);
  }

  private static AccountState pick(List<AccountState> rows, UUID id) {
    return rows.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
  }

  private PaymentState lockPayment(UUID id) {
    return jdbc.sql(PAYMENT_SQL + " FOR UPDATE")
        .param("i", id)
        .query(LedgerService::mapPayment)
        .optional()
        .orElseThrow(() -> LedgerException.notFound("payment " + id));
  }

  private static final String PAYMENT_SQL =
      "SELECT id, payer_account_id, payee_account_id, currency, authorized_minor,"
          + " captured_minor, refunded_minor, status FROM payments WHERE id = :i";

  private static AccountState mapAccount(java.sql.ResultSet rs, int n)
      throws java.sql.SQLException {
    return new AccountState(
        rs.getObject("id", UUID.class),
        rs.getString("currency"),
        rs.getLong("floor_minor"),
        rs.getLong("posted_minor"),
        rs.getLong("held_minor"));
  }

  private static PaymentState mapPayment(java.sql.ResultSet rs, int n)
      throws java.sql.SQLException {
    return new PaymentState(
        rs.getObject("id", UUID.class),
        rs.getObject("payer_account_id", UUID.class),
        rs.getObject("payee_account_id", UUID.class),
        rs.getString("currency"),
        rs.getLong("authorized_minor"),
        rs.getLong("captured_minor"),
        rs.getLong("refunded_minor"),
        PaymentState.Status.valueOf(rs.getString("status")));
  }
}
