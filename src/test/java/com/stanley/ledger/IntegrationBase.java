package com.stanley.ledger;

import com.stanley.ledger.ledger.LedgerException;
import com.stanley.ledger.ledger.LedgerService;
import com.stanley.ledger.ledger.PaymentState;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres and real Redpanda (Kafka API) in containers. Both start once per JVM. Every test
 * starts with empty tables.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationBase {
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
  static final RedpandaContainer REDPANDA =
      new RedpandaContainer(
          DockerImageName.parse("docker.io/redpandadata/redpanda:v25.1.7")
              .asCompatibleSubstituteFor("docker.redpanda.com/redpandadata/redpanda"));

  static {
    POSTGRES.start();
    REDPANDA.start();
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.kafka.bootstrap-servers", REDPANDA::getBootstrapServers);
  }

  @Autowired protected JdbcClient jdbc;
  @Autowired protected LedgerService ledger;

  @BeforeEach
  void cleanTables() {
    jdbc.sql(
            "TRUNCATE journal_entries, journal_transactions, payments, idempotency_keys,"
                + " outbox_events, balance_projection, processed_events, recon_reports, caller_audit,"
                + " accounts CASCADE")
        .update();
  }

  /** A funding account that may go negative, plus n accounts funded with the given amount. */
  protected List<UUID> fundedAccounts(int n, long each) {
    UUID bank = ledger.createAccount("bank", "USD", -1_000_000_000L).id();
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      UUID id = ledger.createAccount("acct-" + i, "USD", 0).id();
      ledger.transfer(bank, id, each);
      ids.add(id);
    }
    ids.add(0, bank);
    return ids;
  }

  /** Random mix of all operation types. Business rejections are expected and ignored. */
  protected void randomWorkload(List<UUID> ids, int ops, long seed) {
    Random rnd = new Random(seed);
    List<UUID> payments = new ArrayList<>();
    for (int i = 0; i < ops; i++) {
      UUID a = ids.get(1 + rnd.nextInt(ids.size() - 1));
      UUID b = ids.get(1 + rnd.nextInt(ids.size() - 1));
      long amount = 1 + rnd.nextInt(3000);
      try {
        switch (rnd.nextInt(5)) {
          case 0 -> ledger.transfer(a, b, amount);
          case 1 -> {
            PaymentState p = ledger.authorize(a, b, amount);
            payments.add(p.id());
          }
          case 2 -> {
            if (!payments.isEmpty())
              ledger.capture(payments.get(rnd.nextInt(payments.size())), null);
          }
          case 3 -> {
            if (!payments.isEmpty()) ledger.voidPayment(payments.get(rnd.nextInt(payments.size())));
          }
          default -> {
            if (!payments.isEmpty()) {
              ledger.refund(payments.get(rnd.nextInt(payments.size())), 1 + rnd.nextInt(500));
            }
          }
        }
      } catch (LedgerException expected) {
        // rejected by a rule, state unchanged
      }
    }
  }
}
