# DECISIONS

Short records of choices, with the reason and the cost.

## D1. Amounts are `long` minor units

Why: floating point cannot hold money exactly. Integers add and compare exactly. `Math.addExact` fails loudly on overflow.
Cost: the API shows cents, not decimals. A currency with no minor unit or with three decimals needs a per-currency scale. Not built.

## D2. Pure rules, thin service

`Rules` has no database, clock or random source. `LedgerService` loads rows under lock, calls a rule and stores the result.
Why: the property test can run the same rules on an in-memory model, with no containers, and run 1,000 sequences in about one second.
Cost: a small amount of mapping code.

## D3. Zero-sum is enforced three times

1. `Outcome` throws if its entries do not sum to zero.
2. A deferred constraint trigger in Postgres checks each journal transaction at commit.
3. Property tests and `scripts/invariants.sh` check it again.

Why: the code can have a bug, the trigger catches it. Cost: one extra query per entry row at commit.

## D4. Cached balance on `accounts`, journal as truth

Why: the floor check needs the balance under lock, and a sum over all entries is slow. The row has a CHECK (`posted - held >= floor`).
The tests prove the cache equals the sum of the entries. Cost: two places that must agree.

## D5. Lock order

Payment row first, then account rows in id order (`ORDER BY id FOR UPDATE`).
Why: two transfers that touch the same pair of accounts in opposite directions would deadlock without a fixed order.
`ConcurrencyIT` ran 2,000 transfers on 50 threads with no deadlock. This is a test result, not a proof.

## D6. Idempotency key row in the same transaction

The key row, the business writes and the stored response commit together.
Why: no window where money moved but the key is lost. A parallel request with the same key waits on the unique index, then replays.
Cost: a failed request rolls back the key too. The client can retry, and the retry runs again. Business errors are not stored.
Keys never expire. Real systems expire them after a time window.

## D7. Transactional outbox, not a direct Kafka send

A direct send after the commit can be lost on a crash. A send before the commit can announce money that never moved.
The outbox row commits with the money. The relay sends later. Delivery is at least once.

## D8. Relay uses `FOR UPDATE SKIP LOCKED`

Two relays can run without taking the same rows. Cost: with two relays, two batches can hold rows of one account,
and the later row can reach Kafka before the earlier one. Order per account is only guaranteed with one relay.
This repo runs one relay. A fix for several relays is to route one account to one relay, for example by hash of the account id.

## D9. Message key is the account id

Kafka orders messages inside one partition. The same key goes to the same partition, so events of one account stay in order.
The topic has 3 partitions. The projection uses additive deltas, so it would also survive reordering, but order keeps the model easy to reason about.

## D10. Consumer dedupes by event id in the same transaction

`processed_events` insert and the balance update commit together. A repeated event inserts nothing, so it changes nothing.
This turns at-least-once delivery into an effectively-once effect on the read model.

## D11. The consumer commits once for each polled batch

One transaction for each record was too slow on this laptop's disk (see PERF.md).
Batch mode uses `SET LOCAL synchronous_commit = off` for these transactions. The read model can be rebuilt, so a lost final commit after a crash is acceptable.
The ledger transactions keep the durable default.
Cost: a failed batch is retried as a whole by the default error handler, then skipped. There is no dead-letter topic.

## D12. The consumer span is created by hand

The OpenTelemetry agent links a batch span to its messages. It does not make it their child.
So the trace of the HTTP request would end at the producer span. The consumer creates one span for each record,
with the producer span as parent (read from the `traceparent` header). I set `messaging.system=kafka`.
This nested consumer span is dropped by default. The compose file sets
`OTEL_INSTRUMENTATION_EXPERIMENTAL_SPAN_SUPPRESSION_STRATEGY=none` to keep it. That setting is marked experimental by the agent.

## D13. Trace context in the outbox row

The outbox row stores the W3C `traceparent` of the request. The relay restores it before it sends.
Why: the relay runs on another thread, later. Without the stored value, the trace would break at the outbox.
When no agent runs (unit and integration tests), the value is null and nothing breaks.

## D14. Redpanda in place of Apache Kafka

Same client API, one small container, fast start. The code uses the plain Kafka client through Spring Kafka.
Cost: this repo shows no knowledge of Kafka cluster operation (brokers, replication, Schema Registry).

## D15. `JdbcClient` and SQL, not Spring Data JDBC repositories

The SQL is the point of this project (locks, SKIP LOCKED, ON CONFLICT). Explicit SQL is easier to explain than generated SQL.

## D16. One Maven module

Packages give the same separation with less build code. A real service might split `ledger` into its own module to keep rules free of Spring.

## D17. Spring Boot 4.1.2

The latest GA line on the build day that supports Java 25 (4.2 was still a release candidate).
Consequence: Boot 4 uses Jackson 3 (`tools.jackson`) and split starters. Many online examples still show Boot 3.
