package com.stanley.ledger.outbox;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls unsent outbox rows and publishes them. FOR UPDATE SKIP LOCKED lets two relays run without
 * taking the same rows. Delivery is at least once: a crash after the send but before the commit
 * sends the row again. The consumer removes duplicates.
 */
@Component
public class OutboxRelay {
  public static final String TOPIC = "ledger.events";
  public static final String CREATED_AT_HEADER = "ledger-created-at";

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final KafkaTemplate<String, String> kafka;
  private final int batchSize;
  private final AtomicBoolean running = new AtomicBoolean(true);

  /** Test hook: simulate a crash after this many sends in a batch. Negative means off. */
  private final AtomicInteger crashAfterSends = new AtomicInteger(-1);

  public OutboxRelay(
      JdbcClient jdbc,
      TransactionTemplate tx,
      KafkaTemplate<String, String> kafka,
      @Value("${ledger.relay.batch-size:500}") int batchSize) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.kafka = kafka;
    this.batchSize = batchSize;
  }

  public void stop() {
    running.set(false);
  }

  public void start() {
    running.set(true);
  }

  public void crashAfterSends(int n) {
    crashAfterSends.set(n);
  }

  @Scheduled(fixedDelayString = "${ledger.relay.poll-ms:200}")
  public void scheduledPoll() {
    if (running.get()) {
      pollOnce();
    }
  }

  /** Publishes one batch. Returns the number of rows marked sent. */
  public int pollOnce() {
    Integer n = tx.execute(status -> pollInTransaction());
    return n == null ? 0 : n;
  }

  private int pollInTransaction() {
    List<Row> rows =
        jdbc.sql(
                "SELECT seq, event_id, aggregate_id, payload, traceparent, created_at FROM outbox_events"
                    + " WHERE sent_at IS NULL ORDER BY seq LIMIT :n FOR UPDATE SKIP LOCKED")
            .param("n", batchSize)
            .query((rs, i) ->
                new Row(
                    rs.getLong("seq"),
                    rs.getObject("event_id", UUID.class),
                    rs.getObject("aggregate_id", UUID.class),
                    rs.getString("payload"),
                    rs.getString("traceparent"),
                    rs.getTimestamp("created_at").getTime()))
            .list();
    int sent = 0;
    List<java.util.concurrent.Future<?>> acks = new java.util.ArrayList<>();
    for (Row r : rows) {
      if (crashAfterSends.get() >= 0 && sent >= crashAfterSends.get()) {
        awaitAll(acks); // the sends before the crash really reach Kafka
        throw new IllegalStateException("simulated relay crash after " + sent + " sends");
      }
      acks.add(publish(r));
      sent++;
    }
    // Sends of one batch are in flight together. The producer keeps order per key.
    awaitAll(acks);
    if (!rows.isEmpty()) {
      jdbc.sql("UPDATE outbox_events SET sent_at = now() WHERE seq = ANY (:seqs)")
          .param("seqs", rows.stream().mapToLong(r -> r.seq).boxed().toArray(Long[]::new))
          .update();
    }
    return rows.size();
  }

  private java.util.concurrent.Future<?> publish(Row r) {
    // Restore the trace of the original HTTP request, so the Kafka producer span joins it.
    Span parent = remoteParent(r.traceparent);
    try (Scope ignored = parent.makeCurrent()) {
      ProducerRecord<String, String> rec =
          new ProducerRecord<>(TOPIC, r.aggregateId.toString(), r.payload);
      rec.headers().add("event-id", r.eventId.toString().getBytes(StandardCharsets.UTF_8));
      rec.headers()
          .add(
              CREATED_AT_HEADER, Long.toString(r.createdAtMillis).getBytes(StandardCharsets.UTF_8));
      return kafka.send(rec);
    }
  }

  private static void awaitAll(List<java.util.concurrent.Future<?>> acks) {
    try {
      for (var f : acks) {
        f.get(30, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (Exception e) {
      throw new IllegalStateException("publish failed", e);
    }
  }

  static Span remoteParent(String traceparent) {
    if (traceparent == null) {
      return Span.getInvalid();
    }
    String[] p = traceparent.split("-");
    if (p.length != 4) {
      return Span.getInvalid();
    }
    SpanContext sc =
        SpanContext.createFromRemoteParent(
            p[1], p[2], TraceFlags.fromHex(p[3], 0), TraceState.getDefault());
    return Span.wrap(sc);
  }

  private record Row(
      long seq,
      UUID eventId,
      UUID aggregateId,
      String payload,
      String traceparent,
      long createdAtMillis) {}
}
