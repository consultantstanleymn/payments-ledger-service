package com.stanley.ledger.ops;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Read-only queries behind the operations console. The controller calls this service. */
@Service
public class OpsQueryService {

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
    throw new UnsupportedOperationException("accounts query is not implemented");
  }

  public PageResult<EntryView> entries(UUID accountId, int limit, String cursor) {
    throw new UnsupportedOperationException("entries query is not implemented");
  }

  public PageResult<PaymentView> payments(String status, int limit, String cursor) {
    throw new UnsupportedOperationException("payments query is not implemented");
  }
}
