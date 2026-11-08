package com.stanley.ledger.projection;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds balance_projection from ledger events. The dedupe row and the balance update share one
 * transaction, so a repeated event changes nothing. This is how at-least-once delivery becomes an
 * effectively-once effect.
 */
@Component
public class ProjectionConsumer {
  private final JdbcClient jdbc;
  private final JsonMapper json;

  public ProjectionConsumer(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @KafkaListener(topics = "ledger.events", groupId = "balance-projection")
  public void onMessage(String payload) {
    apply(payload);
  }

  /** Returns true when the event changed the projection, false when it was a duplicate. */
  @Transactional
  public boolean apply(String payload) {
    JsonNode n = json.readTree(payload);
    UUID eventId = UUID.fromString(n.get("eventId").asString());
    int fresh =
        jdbc.sql("INSERT INTO processed_events (event_id) VALUES (:e) ON CONFLICT DO NOTHING")
            .param("e", eventId)
            .update();
    if (fresh == 0) {
      return false;
    }
    jdbc.sql(
            "INSERT INTO balance_projection (account_id, posted_minor, held_minor, event_count)"
                + " VALUES (:a, :p, :h, 1) ON CONFLICT (account_id) DO UPDATE SET"
                + " posted_minor = balance_projection.posted_minor + EXCLUDED.posted_minor,"
                + " held_minor = balance_projection.held_minor + EXCLUDED.held_minor,"
                + " event_count = balance_projection.event_count + 1")
        .param("a", UUID.fromString(n.get("accountId").asString()))
        .param("p", n.get("postedDelta").asLong())
        .param("h", n.get("heldDelta").asLong())
        .update();
    return true;
  }
}
