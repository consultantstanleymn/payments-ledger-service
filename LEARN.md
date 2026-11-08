# LEARN: how this project works, in plain words

This page is for the owner. Read it top to bottom once. After that, you can explain every part of the project.
Each section has the idea, where the code is, and what to say in an interview.

## 1. The big picture in one minute

A marketplace holds money for buyers and pays sellers. It needs a record of every cent that moves.
This service is that record. It is a **ledger**.

- Accounts hold balances.
- A **payment** has steps: **authorize** (reserve money), **capture** (take it), **void** (cancel the reservation), **refund** (give some back).
- A **transfer** moves money between two accounts.
- Every step is saved as rows in Postgres.
- After the save, other systems learn about it through Kafka.

The hard part is not the code size. The hard part is to be correct when things go wrong: two requests at once,
a client that retries, a crash in the middle, a message that arrives twice. Each pattern below answers one of these.

## 2. Double entry and "entries sum to zero"

Idea: money never appears or disappears. It moves. So every move is written as two or more lines that add up to zero.
Example: buyer pays 35.00 to seller. Lines: buyer -3500, seller +3500. Sum: 0.

Why it helps: if the sum is ever not zero, something is broken, and you can detect it with one query.
That makes the ledger **auditable**.

Where: `Outcome.java` (throws if sum is not zero), the trigger in `V1__ledger.sql` (checks at commit),
`LedgerPropertiesTest` (checks after random operations), `scripts/invariants.sh` (checks after the load test).

Say in an interview: "Every transaction is a set of signed entries that sum to zero. I enforce it in code, in the database with a deferred constraint trigger, and in property tests."

## 3. Why integers in minor units

A computer stores 0.1 as a close but wrong number in floating point. Add many of them and cents go missing.
So money is stored as a whole number of the smallest unit: cents. 35.00 USD is `3500`.
The type is `long`. `Math.addExact` throws an error if a number gets too big, instead of wrapping around silently.
A test (`NoFloatingPointTest`) fails if someone writes `double` or `float` in the ledger package.

Say: "Money is an integer count of minor units. Floating point is banned in the ledger package by a test."

## 4. Holds, posted, held, available

- **posted**: the real balance from journal entries.
- **held**: money reserved by authorized payments that are not yet captured.
- **available** = posted - held. This is what a customer can still spend.
- **floor**: the lowest allowed balance. Normal accounts have floor 0. The "bank" account in tests has a very negative floor, so it can fund others.

An authorization changes only `held`. It writes no journal entries, because no money moved.
A capture writes entries (payer -, payee +) and releases the whole hold. You can capture less than the hold. The rest is freed.

Where: `Rules.java`, `AccountState.java`.

## 5. Pure rules and a thin service

`Rules` is a class of static functions. It gets the current state in, and gives back an `Outcome` (entries and balance changes), or throws.
It never reads the database. The `LedgerService` does the I/O: lock rows, read state, call a rule, write the result.

Why: you can test the rules fast, without containers, on an in-memory copy (`LedgerModel` in the tests).
The property test runs 1,000 random sequences of up to 80 operations and checks the rules after every step.
This is the idea called "functional core, imperative shell".

Say: "Business rules are pure functions. That is why I could property-test them in a second and still trust the database layer."

## 6. Property tests with jqwik

A normal test says: for this input, expect this output. A **property test** says: for ANY input of this kind, this must always hold.
jqwik makes random inputs (here: random lists of operations), runs the property, and if it fails, it shrinks the input to a small example.
Our property: after each operation, all journal transactions sum to zero, no account is below its floor,
the cached balance equals the sum of entries, `held` equals the sum of open authorizations, and total money is unchanged.

Where: `LedgerPropertiesTest`, `LedgerModel`.

## 7. Locking and why there is a fixed order

Two requests can try to spend the same money at the same time. Without protection, both could pass the "enough funds" check and overdraw the account.

Fix: `SELECT ... FOR UPDATE` locks the account rows. The second request waits until the first commits, then reads the new balance.

New problem: deadlock. Request A locks account 1 then waits for account 2. Request B locks account 2 then waits for account 1. Both wait forever.
Fix: always lock in the same order. Here: the payment row first, then accounts sorted by id (`ORDER BY id FOR UPDATE`).
If everyone takes locks in the same order, a cycle cannot form.

Proof in this repo: `ConcurrencyIT` runs 50 threads and 2,000 random transfers among 10 accounts.
Money stays the same, no balance is negative, no deadlock error. Some transfers are rejected because the floor stops them. That is correct behavior.

Say: "Row locks stop double spending. A fixed lock order stops deadlocks. I tested it with 50 threads against real Postgres."

## 8. Idempotency keys

Networks fail. A client sends "pay 35.00", gets no answer, and sends again. Without protection, the buyer pays twice.
The client adds an `Idempotency-Key` header (a unique string it makes up). The server remembers the key and the answer.

- Same key, same body: the server returns the saved answer. Nothing happens a second time. Header `Idempotent-Replay: true`.
- Same key, different body: the client has a bug. The server answers 422.
- Two identical requests at the same moment: the second waits on the unique index in the database until the first commits, then replays.

The key row is written in the same database transaction as the money. So you cannot have "money moved but key lost".

Where: `IdempotentExecutor.java`, `ApiIT`.

Known limit: a request that fails with an error rolls back everything, including its key. A retry runs again. That is fine because nothing was done.

## 9. The dual-write problem and the transactional outbox

Goal: when money moves, tell Kafka.
Naive code: save to Postgres, then send to Kafka. If the app crashes between the two, Postgres has the change and Kafka never hears about it.
Reverse the order and you can announce a payment that then fails to save. The two systems cannot share one transaction. This is the **dual-write problem**.

Fix: the **outbox**. In the same Postgres transaction as the money, insert a row in `outbox_events`. Either both are saved or neither.
A separate **relay** reads unsent rows and publishes them to Kafka, then marks them sent.

If the relay crashes after sending but before marking, it sends again later. So a message can arrive twice.
That is **at-least-once delivery**. It never loses a message, but can duplicate one.

Where: `OutboxWriter.java`, `OutboxRelay.java`, `KafkaFlowIT` (the "relay crash" test).

Say: "I never write to the database and Kafka separately. I write an outbox row in the same transaction and a relay publishes it. Delivery is at least once."

## 10. SKIP LOCKED

The relay reads rows with `FOR UPDATE SKIP LOCKED`. Normally, a second reader would wait for locked rows.
With SKIP LOCKED, it skips them and takes other rows. So two relays can work in parallel without sending the same row.
Trade-off: with two relays, the order of events for one account can break. This project runs one relay, and DECISIONS.md D8 says so.

## 11. Why the Kafka key is the account id

A Kafka topic is split into **partitions**. Order is guaranteed only inside one partition.
The producer picks the partition by hashing the **key**. Same key, same partition, same order.
We use the account id as the key, so all events of one account arrive in order. Different accounts can go in parallel.

## 12. The consumer, dedupe and "effectively once"

Because delivery is at-least-once, the consumer must handle repeats.
Each event has a unique `eventId`. The consumer inserts it into `processed_events` and updates `balance_projection` in one transaction.
`INSERT ... ON CONFLICT DO NOTHING` returns "0 rows" for a repeat, and the consumer then does nothing.
The effect on the balance happens once, even if the message arrives twice. People call this "effectively-once".
It is not Kafka's exactly-once feature. It is dedupe by a unique id.

The consumer reads in batches. One database transaction for a whole batch is much faster than one for each message.
See PERF.md for what happened with the first version.

Where: `ProjectionConsumer.java`, `KafkaFlowIT` (duplicate event test, relay crash test, projection equals ledger test).

## 13. The read model (projection)

`balance_projection` is a second copy of balances, built only from Kafka events. It can lag. It can be rebuilt from the topic.
`/accounts/{id}/balance` reads the ledger and is exact. `/accounts/{id}/balance/projection` reads the copy.
The test "projection equals ledger" computes balances from `journal_entries` and compares them with the projection after a random workload.

This is a small example of **CQRS** thinking (a separate model for reads). Do not claim more than that.

## 14. Reconciliation

A payment processor (like a bank partner) sends a settlement file: what it says it paid out.
We compare it with our own captured payments. Three outcomes: **matched**, **missing** (we have it, the file does not),
**mismatched** (both have it, amounts differ). We also count **unexpected** (in the file, not in our ledger).
Here the processor is a CSV file with `payment_id,amount_minor`. The fixture test has 98 matched, 1 missing, 1 mismatched.

Where: `ReconciliationService.java`, `ReconciliationIT`.
Limit: refunds are not netted. A real job would compare net amounts and dates.

## 15. Flyway

Flyway applies numbered SQL files (`V1__ledger.sql`) in order and remembers which ones ran. The schema is versioned like code.
Rule: never edit a file that has already run. Add `V2__...` instead.

## 16. Testcontainers

A library that starts real Postgres and Redpanda in containers for a test, then stops them. Tests use the real systems, not fakes.
Locally it talks to podman through its socket. In GitHub Actions it talks to Docker.
Testcontainers tests are slower than unit tests, so they are tagged `integration` and run by Failsafe, not Surefire.

## 17. OpenTelemetry and tracing

A **trace** is the story of one request. It is made of **spans**: one for the HTTP call, one for each SQL statement, one for the Kafka send, one for the consumer.
They share a **trace id**. Jaeger draws them as a timeline (see `docs/img/jaeger-trace.png`).

How it works here:
- The OpenTelemetry **Java agent** attaches at start-up and instruments HTTP, JDBC and Kafka by itself. No code change for those.
- I added a few manual spans (`ledger.post ...`).
- The outbox row stores the `traceparent` of the request. The relay restores it before it sends, so the Kafka span joins the same trace.
- The agent puts the trace context into the Kafka message headers. The consumer reads them to continue the trace.
- Spans go to the **Collector**, which writes a file (for the check script) and forwards to Jaeger.

Honest detail: the batch consumer makes its own consumer span per message, because the agent only links batch spans. DECISIONS.md D12 explains it.

`scripts/trace-check.sh` is an automatic test: it reads the file and fails if no single trace contains HTTP, JDBC, Kafka producer and Kafka consumer spans.

## 18. k6 and the load test

k6 runs JavaScript that makes many requests with virtual users (VUs). Our script: 20 VUs for 2 minutes, mixed transfers and reads.
The pass rule is not speed. It is: error rate below 0.1 percent and zero invariant violations.
Reason: speed changes between machines. Correctness must not. Real numbers: 282 requests/s, p95 145 ms (see PERF.md).
**p95** means 95 percent of requests were faster than this value.

## 19. Interview notes

Short answers you must be able to give.

1. **Why do entries sum to zero? Why integers?** Money only moves. A non-zero sum signals a bug and is easy to check. Integers avoid floating point errors.
2. **What is the outbox for?** To avoid the dual-write problem. The event commits with the money. A relay publishes later. At-least-once.
3. **What does at-least-once mean for the consumer?** It can see duplicates. It dedupes by event id in the same transaction as its update.
4. **How do you prevent deadlocks?** Fixed lock order: payment, then accounts by id.
5. **What does SKIP LOCKED do?** The query skips rows locked by others, so several workers can share the queue.
6. **Why is the account id the message key?** Same key goes to the same partition, so events of one account stay in order.
7. **How do you make a retry safe?** Idempotency key, stored with the response in the same transaction.
8. **How do you know the read model is right?** A test compares it with sums computed from the journal after a random workload. The script `invariants.sh` does it after the load test.
9. **What did you learn from the load test?** The first consumer wrote one transaction for each message and fell far behind under load. Batching fixed it. The ledger itself was never wrong.
10. **What would you change for production?** OAuth2, TLS, key expiry, dead-letter topic, several relays with account routing, Schema Registry, real processor files, alerts on outbox lag.

Questions you may get, and honest answers:
- "Is this exactly-once?" No. It is at-least-once with dedupe.
- "Have you run it in production?" No. It is a local project with CI.
- "Did you use a real payment processor?" No. A CSV file stands in. No card data. No PCI scope.
- "Is Redpanda the same as Kafka?" It speaks the Kafka protocol. I used it as a single-node broker. I have not run a Kafka cluster in this project.
- "What happens with two relays?" Events of one account can be sent out of order. I run one relay, and I know the fix: route accounts to relays by hash.

## 20. What is simplified (do not claim these)

- Redpanda single node. No cluster, no Schema Registry, no replication tuning.
- No real processor. No card data. No PCI-DSS.
- One currency per account. No FX.
- At-least-once with dedupe. No Kafka transactions.
- Static API key. No OAuth2, no TLS, no rate limits.
- Not deployed in any cloud. Nothing runs in production.
- No dead-letter topic. A batch that keeps failing is skipped after retries by the default error handler.
- Holds and idempotency keys never expire.
- Reconciliation ignores refunds.
- One relay only. Several relays can reorder events of one account.
- The consumer skips the disk flush at commit (`synchronous_commit = off`) because the read model can be rebuilt.
- The nested consumer span needs an experimental agent setting.
- CI has not run yet. Until it does, say "tested locally" and nothing more.

## 21. Words list

- **Ledger**: the record of all money movements.
- **Journal entry**: one signed line. A transaction is a group of entries that sum to zero.
- **Minor units**: cents, the smallest unit.
- **Hold (authorization)**: money reserved but not yet moved.
- **Idempotent**: doing it twice has the same effect as doing it once.
- **Outbox**: a table of messages to send, saved with the data.
- **At-least-once**: never lost, maybe repeated.
- **Projection (read model)**: a copy of data shaped for reading, built from events.
- **Partition**: one ordered lane of a Kafka topic.
- **Trace, span**: the story of a request, and one step of it.
- **VU**: a virtual user in k6.
- **p95**: the latency that 95 percent of requests beat.
