package com.stanley.ledger.projection;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds balance_projection from ledger events. The dedupe row and the balance update share one
 * transaction, so a repeated event changes nothing. This is how at-least-once delivery becomes an
 * effectively-once effect.
 */
@Component
public class ProjectionConsumer {
  private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("payments-ledger");
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final JsonMapper json;
  private final Timer lag;

  /** Test hook: events with these ids fail each time, as if a dependency was down. */
  private final java.util.Set<UUID> failing = java.util.concurrent.ConcurrentHashMap.newKeySet();

  public void failFor(UUID eventId) {
    failing.add(eventId);
  }

  public void clearFailures() {
    failing.clear();
  }

  public ProjectionConsumer(
      JdbcClient jdbc, TransactionTemplate tx, JsonMapper json, MeterRegistry registry) {
    this.lag = Timer.builder("ledger.projection.lag").register(registry);
    this.jdbc = jdbc;
    this.tx = tx;
    this.json = json;
  }

  /**
   * One database transaction per polled batch, not per event. Each event is still deduplicated by
   * its own id. A batch that fails is retried as a whole, and the dedupe rows make that safe.
   */
  @KafkaListener(topics = "ledger.events", groupId = "balance-projection", batch = "true")
  public void onBatch(List<ConsumerRecord<String, String>> records) {
    try {
      tx.executeWithoutResult(status -> {
        // The projection can be rebuilt from Kafka, so a lost last commit after a crash is fine.
        jdbc.sql("SET LOCAL synchronous_commit = off").update();
        for (ConsumerRecord<String, String> record : records) {
          applyWithSpan(record);
        }
      });
    } catch (RuntimeException batchFailure) {
      // Slow path: one transaction per record. The records before the bad one stay committed.
      // The dedupe rows make the repeat of already applied records safe.
      for (int i = 0; i < records.size(); i++) {
        try {
          ConsumerRecord<String, String> record = records.get(i);
          tx.executeWithoutResult(status -> applyWithSpan(record));
        } catch (RuntimeException e) {
          throw new BatchListenerFailedException("record failed: " + e.getMessage(), e, i);
        }
      }
    }
  }

  /**
   * The OpenTelemetry agent links a batch span to its messages and does not parent it. To keep one
   * trace from the HTTP request to the projection, this makes one consumer span per record. Its
   * parent is the producer span, read from the traceparent header that the agent put on the record.
   */
  private void applyWithSpan(ConsumerRecord<String, String> record) {
    Header h = record.headers().lastHeader("traceparent");
    Context parent =
        W3CTraceContextPropagator.getInstance()
            .extract(
                Context.root(),
                h == null ? null : new String(h.value(), StandardCharsets.UTF_8),
                TRACEPARENT);
    Span span =
        TRACER
            .spanBuilder(record.topic() + " process")
            .setSpanKind(SpanKind.CONSUMER)
            .setParent(parent)
            .setAttribute("messaging.system", "kafka")
            .setAttribute("messaging.operation", "process")
            .setAttribute("messaging.destination.name", record.topic())
            .startSpan();
    try (Scope ignored = span.makeCurrent()) {
      if (applyOne(record.value())) {
        recordLag(record);
      }
    } finally {
      span.end();
    }
  }

  /** Time from outbox insert to projection apply. No sample when the header is absent. */
  private void recordLag(ConsumerRecord<String, String> record) {
    Header created = record.headers().lastHeader("ledger-created-at");
    if (created == null) {
      return;
    }
    try {
      long millis =
          System.currentTimeMillis()
              - Long.parseLong(new String(created.value(), StandardCharsets.UTF_8));
      lag.record(Duration.ofMillis(Math.max(0, millis)));
    } catch (NumberFormatException ignored) {
      // A bad header gives no sample.
    }
  }

  private static final TextMapGetter<String> TRACEPARENT =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(String carrier) {
          return List.of("traceparent");
        }

        @Override
        public String get(String carrier, String key) {
          return "traceparent".equals(key) ? carrier : null;
        }
      };

  /** Returns true when the event changed the projection, false when it was a duplicate. */
  public boolean apply(String payload) {
    Boolean changed = tx.execute(status -> applyOne(payload));
    return Boolean.TRUE.equals(changed);
  }

  private boolean applyOne(String payload) {
    JsonNode n;
    UUID eventId;
    UUID accountId;
    long posted;
    long held;
    try {
      n = json.readTree(payload);
      eventId = UUID.fromString(n.get("eventId").asString());
      accountId = UUID.fromString(n.get("accountId").asString());
      posted = n.get("postedDelta").asLong();
      held = n.get("heldDelta").asLong();
    } catch (RuntimeException e) {
      throw new PoisonEventException("event payload is malformed", e);
    }
    if (failing.contains(eventId)) {
      throw new PoisonEventException("simulated transient failure", null);
    }
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
        .param("a", accountId)
        .param("p", posted)
        .param("h", held)
        .update();
    return true;
  }
}
