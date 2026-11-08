# PERF

Real numbers from one run on 2026-11-07. Treat them as a laptop result. Do not compare them with a server.

## Machine and settings

- Intel Core i7-4770HQ (4 cores, 8 threads), 16 GB RAM, MacBook Pro mid 2015, Fedora 44, encrypted btrfs.
- The laptop was also busy with a browser, an editor and other builds. The k6 process ran with `nice -n 10`.
- App, Postgres 16, Redpanda, Collector and Jaeger ran in rootless podman on the same machine as k6.
- The app ran with the OpenTelemetry Java agent on and `-Xmx384m`.
- Postgres used default durable settings (`fsync` on).
- Command: `k6 run --summary-trend-stats="avg,med,p(90),p(95),p(99),max" load/ledger.js`.
- Script: 20 VUs for 2 minutes. About 70 percent transfers between 20 accounts, 30 percent balance reads.
  Every transfer has a new Idempotency-Key. Amounts are small, so the floor never rejects a transfer.

## Result (final run)

| Metric | Value |
|---|---|
| Iterations | 33,966 |
| Requests per second (all requests) | 282.2 |
| Latency p50 | 63.6 ms |
| Latency p90 | 121.7 ms |
| Latency p95 | 144.9 ms |
| Latency p99 | 199.4 ms |
| Latency max | 362.8 ms |
| Error rate (`http_req_failed`) | 0.00 percent (0 of 34,028) |
| `invariant_violations` | 0 |

The run before it (same settings, no p99 in the summary) gave 291.5 requests/s, p50 61.9 ms, p95 137.1 ms and 0.00 percent errors.
The two runs differ by about 3 percent. This is the normal noise on this laptop.

## Invariants after the run (`scripts/invariants.sh`)

```
unbalanced_transactions=0 accounts_below_floor=0 cache_vs_journal_diffs=0 projection_vs_ledger_diffs=0 sum_of_all_balances=0 outbox_pending=0
```

The read model had caught up with the ledger when the script ran (`outbox_pending=0`).

## Gates

The test fails on an error rate of 0.1 percent or more, or on any invariant violation.
There is no latency gate. A laptop and a CI runner differ too much.

## What an earlier version taught

The first version of the consumer used one database transaction for each Kafka record.
After a run like the one above, the read model needed many minutes to catch up with the ledger.
Postgres wait events showed WAL write and WAL sync, on an encrypted disk. The consumer now commits once for each polled batch.
The same kind of backlog (about 54,000 events) cleared in under two minutes. I did not time it more closely.
The projection is allowed to lag the ledger. `GET /accounts/{id}/balance` reads the ledger and is always exact.
`GET /accounts/{id}/balance/projection` can be behind for a short time.

## Not measured

- No p99 gate, no soak test, no test with many app instances.
- No measurement of the relay with several instances. Two relays can reorder events of one account (see DECISIONS.md).
- No test on `ubuntu-latest`. The CI `k6-smoke` job (5 VUs, 30 seconds) has not run.
