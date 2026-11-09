package com.stanley.ledger.projection;

import com.stanley.ledger.KafkaConfig;
import com.stanley.ledger.outbox.OutboxRelay;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads records from the dead-letter topic and publishes them again to the event topic. The
 * projection removes repeats with processed_events, so a record applies once even if it is redriven
 * twice.
 */
@Component
public class DltRedriver {
  public static final String REDRIVEN_HEADER = "ledger-redriven";
  static final String GROUP = "dlt-redriver";

  private final ConsumerFactory<String, String> consumers;
  private final KafkaTemplate<String, String> kafka;

  public DltRedriver(
      ConsumerFactory<String, String> consumers, KafkaTemplate<String, String> kafka) {
    this.consumers = consumers;
    this.kafka = kafka;
  }

  /** Counts of records read and published. Offsets are committed only after the publish. */
  public record Result(int read, int republished) {}

  public Result redrive(int max) {
    return redrive(max, false);
  }

  /** With fromBeginning, the redriver reads the topic again from offset 0 (used by tests). */
  public Result redrive(int max, boolean fromBeginning) {
    String group = fromBeginning ? GROUP + "-replay-" + System.nanoTime() : GROUP;
    var props = new java.util.Properties();
    props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Math.max(1, max));
    int read = 0;
    int sent = 0;
    try (var consumer = consumers.createConsumer(group, "", "", props)) {
      consumer.subscribe(List.of(KafkaConfig.DLT));
      long deadline = System.currentTimeMillis() + 10_000;
      List<java.util.concurrent.Future<?>> acks = new ArrayList<>();
      while (read < max && System.currentTimeMillis() < deadline) {
        var batch = consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, String> r : batch) {
          if (read >= max) break;
          read++;
          var out = new ProducerRecord<>(OutboxRelay.TOPIC, r.key(), r.value());
          out.headers().add(REDRIVEN_HEADER, "true".getBytes(StandardCharsets.UTF_8));
          acks.add(kafka.send(out));
        }
        if (!batch.isEmpty()) break;
      }
      for (var f : acks) {
        f.get(30, TimeUnit.SECONDS);
        sent++;
      }
      if (read > 0) {
        consumer.commitSync();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (Exception e) {
      throw new IllegalStateException("redrive failed", e);
    }
    return new Result(read, sent);
  }
}
