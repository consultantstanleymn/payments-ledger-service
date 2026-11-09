package com.stanley.ledger.qa.support;

import com.stanley.ledger.qa.config.QaConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only JDBC view of the ledger database. It checks what the service stored, not what the API
 * returned. Every statement uses bind parameters and lives in a constant.
 */
public final class LedgerDb {
  private static final String SQL_TXN_EXISTS =
      "SELECT count(*) FROM journal_transactions WHERE id = ?";

  private static final String SQL_TXN_SUM =
      "SELECT COALESCE(SUM(amount_minor), 0) FROM journal_entries WHERE txn_id = ?";

  private static final String SQL_CACHED_MATCHES =
      "SELECT a.posted_minor = COALESCE((SELECT SUM(e.amount_minor)"
          + " FROM journal_entries e WHERE e.account_id = a.id), 0)"
          + " FROM accounts a WHERE a.id = ?";

  private static final String SQL_PAYMENT_STATUS = "SELECT status FROM payments WHERE id = ?";

  private static final String SQL_ENTRIES =
      "SELECT id, txn_id, account_id, amount_minor FROM journal_entries"
          + " WHERE account_id = ? ORDER BY id";

  private static final String SQL_ACCOUNTS_FOR_RUN =
      "SELECT id FROM accounts WHERE name LIKE ? ORDER BY created_at, id";

  private static final String SQL_TXNS_FOR_REFERENCE =
      "SELECT id FROM journal_transactions WHERE reference = ? ORDER BY created_at, id";

  private static final String SQL_INSERT_PROBE =
      "INSERT INTO accounts (id, name, currency) VALUES (?, ?, ?)";

  public boolean journalBalancedFor(List<UUID> txnIds) {
    if (txnIds == null) {
      return false;
    }
    try (Connection connection = connect()) {
      for (UUID txnId : txnIds) {
        if (count(connection, SQL_TXN_EXISTS, txnId) == 0L) {
          return false;
        }
        if (sum(connection, SQL_TXN_SUM, txnId) != 0L) {
          return false;
        }
      }
      return true;
    } catch (SQLException e) {
      throw new IllegalStateException("journal balance check failed", e);
    }
  }

  public boolean cachedBalanceMatchesJournal(UUID accountId) {
    try (Connection connection = connect();
        PreparedStatement statement = connection.prepareStatement(SQL_CACHED_MATCHES)) {
      statement.setObject(1, accountId);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() && rows.getBoolean(1);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("cached balance check failed", e);
    }
  }

  public String paymentStatus(UUID paymentId) {
    try (Connection connection = connect();
        PreparedStatement statement = connection.prepareStatement(SQL_PAYMENT_STATUS)) {
      statement.setObject(1, paymentId);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? rows.getString(1) : null;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("payment status check failed", e);
    }
  }

  public List<JournalEntry> entriesFor(UUID accountId) {
    try (Connection connection = connect();
        PreparedStatement statement = connection.prepareStatement(SQL_ENTRIES)) {
      statement.setObject(1, accountId);
      try (ResultSet rows = statement.executeQuery()) {
        List<JournalEntry> entries = new ArrayList<>();
        while (rows.next()) {
          entries.add(
              new JournalEntry(
                  rows.getLong("id"),
                  rows.getObject("txn_id", UUID.class),
                  rows.getObject("account_id", UUID.class),
                  rows.getLong("amount_minor")));
        }
        return entries;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("journal entry read failed", e);
    }
  }

  public List<UUID> accountsForRun(String runId) {
    String prefix = String.format("qa-%s-%%", runId);
    try (Connection connection = connect();
        PreparedStatement statement = connection.prepareStatement(SQL_ACCOUNTS_FOR_RUN)) {
      statement.setString(1, prefix);
      try (ResultSet rows = statement.executeQuery()) {
        List<UUID> ids = new ArrayList<>();
        while (rows.next()) {
          ids.add(rows.getObject("id", UUID.class));
        }
        return ids;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("run account read failed", e);
    }
  }

  public List<UUID> transactionIdsForReference(UUID reference) {
    try (Connection connection = connect();
        PreparedStatement statement = connection.prepareStatement(SQL_TXNS_FOR_REFERENCE)) {
      statement.setString(1, reference.toString());
      try (ResultSet rows = statement.executeQuery()) {
        List<UUID> ids = new ArrayList<>();
        while (rows.next()) {
          ids.add(rows.getObject("id", UUID.class));
        }
        return ids;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("journal transaction read failed", e);
    }
  }

  public boolean isReadOnly() {
    try (Connection connection = connect()) {
      return connection.isReadOnly();
    } catch (SQLException e) {
      throw new IllegalStateException("read-only check failed", e);
    }
  }

  public boolean writeIsRejected() {
    try (Connection connection = connect()) {
      connection.setAutoCommit(false);
      try (PreparedStatement statement = connection.prepareStatement(SQL_INSERT_PROBE)) {
        statement.setObject(1, UUID.randomUUID());
        statement.setString(2, "qa-readonly-probe");
        statement.setString(3, "USD");
        statement.executeUpdate();
      }
      connection.rollback();
      return false;
    } catch (SQLException e) {
      return "25006".equals(e.getSQLState());
    }
  }

  private static Connection connect() throws SQLException {
    Connection connection =
        DriverManager.getConnection(
            QaConfig.dbUrl(), QaConfig.dbUser(), QaConfig.dbPassword());
    connection.setReadOnly(true);
    return connection;
  }

  private static long count(Connection connection, String sql, UUID id) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, id);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? rows.getLong(1) : 0L;
      }
    }
  }

  private static long sum(Connection connection, String sql, UUID id) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, id);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? rows.getLong(1) : 0L;
      }
    }
  }

  public record JournalEntry(long id, UUID transactionId, UUID accountId, long amountMinor) {}
}
