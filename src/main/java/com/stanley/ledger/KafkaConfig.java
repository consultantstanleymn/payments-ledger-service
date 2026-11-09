package com.stanley.ledger;

import com.stanley.ledger.outbox.OutboxRelay;
import com.stanley.ledger.projection.PoisonEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {
  public static final String DLT = OutboxRelay.TOPIC + ".DLT";

  /** Three partitions. The message key is the account id, so one account stays in one partition. */
  @Bean
  NewTopic ledgerEvents() {
    return TopicBuilder.name(OutboxRelay.TOPIC).partitions(3).replicas(1).build();
  }

  /**
   * The dead-letter topic has the same partition count, because the recoverer keeps the partition.
   */
  @Bean
  NewTopic ledgerEventsDlt() {
    return TopicBuilder.name(DLT).partitions(3).replicas(1).build();
  }

  /**
   * A record that fails twice more after a 500 ms wait goes to the dead-letter topic. A poison
   * event skips the retries. Records before the failing one in a batch stay committed.
   */
  @Bean
  CommonErrorHandler projectionErrorHandler(KafkaTemplate<String, String> template) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            template, (record, ex) -> new TopicPartition(DLT, record.partition()));
    var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 2L));
    handler.addNotRetryableExceptions(
        PoisonEventException.class, tools.jackson.core.JacksonException.class);
    return handler;
  }
}
