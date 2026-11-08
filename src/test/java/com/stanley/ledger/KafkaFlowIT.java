package com.stanley.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stanley.ledger.outbox.OutboxRelay;
import com.stanley.ledger.projection.ProjectionConsumer;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class KafkaFlowIT extends IntegrationBase {
  @Autowired OutboxRelay relay;
  @Autowired ProjectionConsumer consumer;

  final JsonMapper json = JsonMapper.builder().build();

  @AfterEach
  void restore() {
    relay.crashAfterSends(-1);
    relay.start();
  }

  long outboxCount() {
    return jdbc.sql("SELECT count(*) FROM outbox_events").query(Long.class).single();
  }

  void drainRelay() {
    relay.crashAfterSends(-1);
    while (relay.pollOnce() > 0) {}
  }

  void awaitProjection(long expectedEvents) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 90_000;
    while (System.currentTimeMillis() < deadline) {
      long n = jdbc.sql("SELECT count(*) FROM processed_events").query(Long.class).single();
      if (n >= expectedEvents) return;
      Thread.sleep(250);
    }
    throw new AssertionError("projection did not catch up");
  }

  /** Reads the whole topic from the start. Returns the number of times each event id was seen. */
  Map<String, Integer> readTopic(Set<String> wanted) {
    Properties p = new Properties();
    p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers());
    p.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
    p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    Map<String, Integer> seen = new HashMap<>();
    try (var c = new KafkaConsumer<String, String>(p)) {
      c.subscribe(List.of(OutboxRelay.TOPIC));
      long deadline = System.currentTimeMillis() + 30_000;
      int idle = 0;
      // wait until every wanted id was seen, then read until the topic is quiet
      while (System.currentTimeMillis() < deadline && (seen.size() < wanted.size() || idle < 3)) {
        var recs = c.poll(Duration.ofSeconds(1));
        idle = recs.isEmpty() ? idle + 1 : 0;
        for (ConsumerRecord<String, String> r : recs) {
          String id = json.readTree(r.value()).get("eventId").asString();
          if (wanted.contains(id)) seen.merge(id, 1, Integer::sum);
        }
      }
    }
    return seen;
  }

  @Test
  void relayCrashMidBatchStillDeliversEveryEventAndProjectionCountsOnce() throws Exception {
    List<UUID> ids = fundedAccounts(6, 50_000);
    relay.stop(); // the scheduled poller stays quiet, the test drives the relay by hand
    drainRelay();
    awaitProjection(outboxCount()); // setup events are fully consumed before the real test
    jdbc.sql("DELETE FROM outbox_events WHERE sent_at IS NOT NULL").update();
    jdbc.sql("TRUNCATE processed_events, balance_projection").update();
    randomWorkload(ids, 150, 42);
    long total = outboxCount();
    assertThat(total).isGreaterThan(30);

    relay.crashAfterSends(7);
    assertThatThrownBy(relay::pollOnce).hasMessageContaining("simulated relay crash");
    long unsentAfterCrash =
        jdbc.sql("SELECT count(*) FROM outbox_events WHERE sent_at IS NULL")
            .query(Long.class)
            .single();
    assertThat(unsentAfterCrash).as("the crashed batch rolled back").isEqualTo(total);

    drainRelay(); // "restart": the same rows are sent again, 7 of them for the second time
    Set<String> eventIds =
        new HashSet<>(
            jdbc.sql("SELECT event_id::text FROM outbox_events").query(String.class).list());
    awaitProjection(total);

    Map<String, Integer> seen = readTopic(eventIds);
    assertThat(seen.keySet()).as("every event reached Kafka").isEqualTo(eventIds);
    long duplicates = seen.values().stream().filter(v -> v > 1).count();
    assertThat(duplicates)
        .as("at least once: the crashed 7 arrive twice")
        .isGreaterThanOrEqualTo(7);

    long processed = jdbc.sql("SELECT count(*) FROM processed_events").query(Long.class).single();
    long applied =
        jdbc.sql("SELECT sum(event_count) FROM balance_projection").query(Long.class).single();
    assertThat(processed).isEqualTo(total);
    assertThat(applied).as("projection applied each event once").isEqualTo(total);
    System.out.println(
        "OUTBOX events="
            + total
            + " duplicates_on_topic="
            + duplicates
            + " projection_applied="
            + applied);
  }

  @Test
  void samePayloadTwiceChangesTheProjectionOnce() {
    UUID account = UUID.randomUUID();
    String payload =
        "{\"version\":1,\"eventId\":\"%s\",\"type\":\"TEST\",\"reference\":\"%s\",\"accountId\":\"%s\",\"postedDelta\":250,\"heldDelta\":0}"
            .formatted(UUID.randomUUID(), UUID.randomUUID(), account);
    assertThat(consumer.apply(payload)).isTrue();
    assertThat(consumer.apply(payload)).isFalse();
    assertThat(
            jdbc.sql("SELECT posted_minor FROM balance_projection WHERE account_id = :a")
                .param("a", account)
                .query(Long.class)
                .single())
        .isEqualTo(250);
  }

  @Test
  void projectionEqualsTheLedgerAfterARandomWorkload() throws Exception {
    List<UUID> ids = fundedAccounts(8, 30_000);
    randomWorkload(ids, 300, 7);
    long total = outboxCount();
    awaitProjection(total);

    long mismatches =
        jdbc.sql(
                """
                SELECT count(*) FROM accounts a
                LEFT JOIN balance_projection p ON p.account_id = a.id
                WHERE COALESCE(p.posted_minor, 0) <>
                        COALESCE((SELECT sum(amount_minor) FROM journal_entries e WHERE e.account_id = a.id), 0)
                   OR COALESCE(p.held_minor, 0) <>
                        COALESCE((SELECT sum(authorized_minor) FROM payments y
                                  WHERE y.payer_account_id = a.id AND y.status = 'AUTHORIZED'), 0)
                """)
            .query(Long.class)
            .single();
    assertThat(mismatches).isZero();
    long accountsChecked = jdbc.sql("SELECT count(*) FROM accounts").query(Long.class).single();
    System.out.println("PROJECTION accounts_checked=" + accountsChecked + " events=" + total);
  }
}
