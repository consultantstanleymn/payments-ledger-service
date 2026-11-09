# Runbook: SLO alerts, dead letters, rollback

The base procedures are in [`docs/RUNBOOK.md`](../RUNBOOK.md). This file adds the entries that the alert rules link to.

## availability-burn

Alert: `LedgerTransferAvailabilityBurnFast` or `BurnSlow`. Valid transfer requests fail with 5xx.

1. Check `GET /actuator/health` and `podman logs ledger_app_1`.
2. Check Postgres: `podman exec ledger_postgres_1 pg_isready -U ledger`.
3. If Postgres restarted, the pool recovers by itself (failure lab 2). If the app is down, start it.
4. Run `scripts/invariants.sh`. All values must be 0.

## latency-burn

Alert: `LedgerTransferLatencyBurnFast` or `BurnSlow`. Fewer than 99 percent of transfers finish in 300 ms.

1. Look for database latency first (failure lab 5 shows the pattern).
2. Check CPU load and Postgres connections.
3. Check for a long-running query in `pg_stat_activity`.
4. Reduce load or fix the slow path. Do not raise the target to silence the alert.

## freshness-burn

Alert: `LedgerTransferFreshnessBurnFast` or `BurnSlow`. The projection is more than 5 s behind.

1. Count unsent rows: `SELECT count(*) FROM outbox_events WHERE sent_at IS NULL`.
2. If the count grows, Kafka or the relay is down (failure lab 1). Start Redpanda. The backlog drains.
3. If the count is 0, check the consumer group: `rpk group describe balance-projection`.
4. Run `scripts/invariants.sh` after the backlog drains.

## dlt-not-empty

The dead-letter topic `ledger.events.DLT` holds records that the projection could not apply.

1. Read them: `rpk topic consume ledger.events.DLT -n 5` inside the Redpanda container.
2. The exception headers name the cause (`kafka_dlt-exception-fqcn`, `kafka_dlt-exception-message`).
3. A transient cause: fix it, then follow the redrive procedure.
4. A malformed record: a repeat cannot fix it. A person decides. Keep the record and write the decision in the ticket (failure lab 4).

## redrive-procedure

1. Set `LEDGER_ADMIN_KEY` in the app environment. Without it the route answers 403.
2. Run `LEDGER_ADMIN_KEY=... scripts/redrive-dlt.sh 100`.
3. The answer shows `read` and `republished`. The projection applies each event once, because `processed_events` removes repeats.
4. Run `scripts/invariants.sh`.

## rollback-procedure

1. Read the digest of the previous image from the release record. Do not use a tag.
2. Stop the new container. Start the old image by digest on the same database. Set `SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS=*:future` if the old image does not carry the setting.
3. Check `GET /actuator/health`, then run `scripts/invariants.sh`.
4. Rollback is safe only while all migrations are additive. See [ROLLOUT.md](ROLLOUT.md). `scripts/rollout.sh` rehearses the steps.

## caller-audit-write-failures

The counter `ledger_caller_audit_write_failures_total` grows when an audit row cannot be written. The request still succeeds (ADR 0002).

1. Check the app log for `caller audit write failed`.
2. Check the database for disk, locks and the trigger `caller_audit_no_change`.
3. After the fix, list the gap by comparing `idempotency_key` values in `idempotency_keys` with `caller_audit`.
4. Record the gap in the incident ticket. The missing rows cannot be recreated.
