package com.stanley.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.stanley.ledger.ledger.LedgerException;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConcurrencyIT extends IntegrationBase {

  @Test
  void fiftyThreadsTwoThousandTransfersKeepTheMoneyAndNeverDeadlock() throws Exception {
    List<UUID> all = fundedAccounts(10, 100_000);
    List<UUID> accounts = all.subList(1, all.size());
    long before = total(accounts);
    ExecutorService pool = Executors.newFixedThreadPool(50);
    CountDownLatch start = new CountDownLatch(1);
    ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
    AtomicInteger done = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();
    for (int t = 0; t < 50; t++) {
      long seed = t;
      pool.submit(() -> {
        Random rnd = new Random(seed);
        try {
          start.await();
          for (int i = 0; i < 40; i++) {
            UUID a = accounts.get(rnd.nextInt(accounts.size()));
            UUID b = accounts.get(rnd.nextInt(accounts.size()));
            if (a.equals(b)) {
              b = accounts.get((accounts.indexOf(a) + 1) % accounts.size());
            }
            // large amounts so that some transfers hit the floor and are rejected
            try {
              ledger.transfer(a, b, 1 + rnd.nextInt(60_000));
              done.incrementAndGet();
            } catch (LedgerException e) {
              rejected.incrementAndGet();
            }
          }
        } catch (Throwable e) {
          unexpected.add(e);
        }
      });
    }
    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(5, TimeUnit.MINUTES)).isTrue();

    assertThat(unexpected).as("no deadlock or other error").isEmpty();
    assertThat(done.get() + rejected.get()).isEqualTo(2000);
    assertThat(done.get()).as("some transfers succeeded").isGreaterThan(500);
    assertThat(rejected.get()).as("some transfers hit the floor").isGreaterThan(0);
    assertThat(total(accounts)).isEqualTo(before);
    assertThat(
            jdbc.sql("SELECT count(*) FROM accounts WHERE posted_minor < floor_minor")
                .query(Long.class)
                .single())
        .isZero();
    assertThat(
            jdbc.sql(
                    "SELECT count(*) FROM accounts a WHERE a.posted_minor <> COALESCE((SELECT"
                        + " sum(amount_minor) FROM journal_entries e WHERE e.account_id = a.id), 0)")
                .query(Long.class)
                .single())
        .as("cached balance equals journal for every account")
        .isZero();
    System.out.println("CONCURRENCY transfers_ok=" + done + " rejected=" + rejected);
  }

  private long total(List<UUID> ids) {
    return jdbc.sql("SELECT sum(posted_minor) FROM accounts WHERE id = ANY (:ids)")
        .param("ids", ids.toArray(new UUID[0]))
        .query(Long.class)
        .single();
  }
}
