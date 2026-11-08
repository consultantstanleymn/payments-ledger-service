package com.stanley.ledger;

import com.stanley.ledger.outbox.OutboxRelay;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
  /** Three partitions. The message key is the account id, so one account stays in one partition. */
  @Bean
  NewTopic ledgerEvents() {
    return TopicBuilder.name(OutboxRelay.TOPIC).partitions(3).replicas(1).build();
  }
}
