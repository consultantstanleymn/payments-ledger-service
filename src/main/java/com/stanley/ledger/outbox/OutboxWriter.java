package com.stanley.ledger.outbox;

import com.stanley.ledger.ledger.Delta;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Writes outbox rows. It must run inside the same transaction as the journal write. */
@Component
public class OutboxWriter {
  public static final int EVENT_VERSION = 1;

  private final JdbcClient jdbc;
  private final JsonMapper json;

  public OutboxWriter(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void write(String type, UUID reference, Delta delta) {
    if (delta.isZero()) {
      return;
    }
    UUID eventId = UUID.randomUUID();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("version", EVENT_VERSION);
    payload.put("eventId", eventId.toString());
    payload.put("type", type);
    payload.put("reference", reference.toString());
    payload.put("accountId", delta.accountId().toString());
    payload.put("postedDelta", delta.postedDelta());
    payload.put("heldDelta", delta.heldDelta());
    jdbc.sql(
            "INSERT INTO outbox_events (event_id, aggregate_id, type, payload, traceparent)"
                + " VALUES (:id, :agg, :type, :payload, :tp)")
        .param("id", eventId)
        .param("agg", delta.accountId())
        .param("type", type)
        .param("payload", json.writeValueAsString(payload))
        .param("tp", currentTraceparent())
        .update();
  }

  static String currentTraceparent() {
    SpanContext sc = Span.current().getSpanContext();
    if (!sc.isValid()) {
      return null;
    }
    return "00-" + sc.getTraceId() + "-" + sc.getSpanId() + "-" + sc.getTraceFlags().asHex();
  }
}
