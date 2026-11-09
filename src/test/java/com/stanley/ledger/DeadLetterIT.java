package com.stanley.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

class DeadLetterIT extends IntegrationBase {
  @org.springframework.beans.factory.annotation.Autowired
  com.stanley.ledger.projection.ProjectionConsumer consumer;

  @org.springframework.beans.factory.annotation.Autowired
  com.stanley.ledger.projection.DltRedriver redriver;

  static final String TOPIC = "ledger.events";
  static final String DLT = "ledger.events.DLT";

  String bootstrap() {
    return REDPANDA.getBootstrapServers();
  }

  void produce(String key, String value) throws Exception {
    Properties p = new Properties();
    p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap());
    p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    try (var producer = new KafkaProducer<String, String>(p)) {
      producer.send(new ProducerRecord<>(TOPIC, key, value)).get();
    }
  }

  String goodEvent(UUID eventId, UUID account) {
    return "{\"eventId\":\"%s\",\"accountId\":\"%s\",\"postedDelta\":100,\"heldDelta\":0}"
        .formatted(eventId, account);
  }

  /** Reads the dead-letter topic from the start with a fresh group and returns matching records. */
  List<ConsumerRecord<String, String>> dltRecordsWith(String needle) {
    Properties p = new Properties();
    p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap());
    p.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID());
    p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    long deadline = System.currentTimeMillis() + 60_000;
    try (var consumer = new KafkaConsumer<String, String>(p)) {
      consumer.subscribe(List.of(DLT));
      while (System.currentTimeMillis() < deadline) {
        var found = new java.util.ArrayList<ConsumerRecord<String, String>>();
        for (var r : consumer.poll(Duration.ofMillis(500))) {
          if (r.value().contains(needle)) found.add(r);
        }
        if (!found.isEmpty()) return found;
      }
    }
    return List.of();
  }

  long processed() {
    return jdbc.sql("SELECT count(*) FROM processed_events").query(Long.class).single();
  }

  void awaitProcessed(long expected) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 60_000;
    while (System.currentTimeMillis() < deadline && processed() < expected) {
      Thread.sleep(300);
    }
    assertThat(processed()).isEqualTo(expected);
  }

  void awaitEvent(UUID id) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 60_000;
    while (System.currentTimeMillis() < deadline) {
      long n =
          jdbc.sql("SELECT count(*) FROM processed_events WHERE event_id = :id")
              .param("id", id)
              .query(Long.class)
              .single();
      if (n > 0) return;
      Thread.sleep(300);
    }
    throw new AssertionError("event was not applied: " + id);
  }

  @Test
  void poisonEventGoesToDltAndOthersApply() throws Exception {
    String marker = "poison-" + UUID.randomUUID();
    produce("bad-key", "{not json " + marker);
    UUID good = UUID.randomUUID();
    produce("good-key", goodEvent(good, UUID.randomUUID()));
    awaitProcessed(1);
    assertThat(dltRecordsWith(marker)).hasSize(1);
    assertThat(jdbc.sql("SELECT count(*) FROM balance_projection").query(Long.class).single())
        .isEqualTo(1);
  }

  @Test
  void dltRecordCarriesExceptionHeaders() throws Exception {
    String marker = "headers-" + UUID.randomUUID();
    produce("bad-key-2", "{broken " + marker);
    var records = dltRecordsWith(marker);
    assertThat(records).hasSize(1);
    var headers = records.get(0).headers();
    assertThat(headers.lastHeader("kafka_dlt-exception-fqcn")).isNotNull();
    assertThat(headers.lastHeader("kafka_dlt-original-topic")).isNotNull();
  }

  @Test
  void redriveAppliesFixedEventOnce() throws Exception {
    UUID id = UUID.randomUUID();
    consumer.failFor(id);
    produce("redrive-key", goodEvent(id, UUID.randomUUID()));
    assertThat(dltRecordsWith(id.toString())).hasSize(1);
    assertThat(processed()).isZero();
    consumer.clearFailures();
    var result = redriver.redrive(1000, true);
    assertThat(result.republished()).isPositive();
    awaitEvent(id);
  }

  @Test
  void redriveTwiceDoesNotDoubleApply() throws Exception {
    UUID id = UUID.randomUUID();
    consumer.failFor(id);
    produce("redrive-key-2", goodEvent(id, UUID.randomUUID()));
    assertThat(dltRecordsWith(id.toString())).hasSize(1);
    consumer.clearFailures();
    redriver.redrive(1000, true);
    awaitEvent(id);
    redriver.redrive(1000, true);
    Thread.sleep(3000);
    assertThat(
            jdbc.sql("SELECT count(*) FROM processed_events WHERE event_id = :id")
                .param("id", id)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    // Two full replays still leave exactly one projection step for this account.
    assertThat(
            jdbc.sql("SELECT max(event_count) FROM balance_projection").query(Long.class).single())
        .isEqualTo(1);
  }
}
