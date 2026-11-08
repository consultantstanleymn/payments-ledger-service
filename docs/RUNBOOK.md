# RUNBOOK

Local operations. This service is not deployed anywhere. Treat the steps as the procedure you would adapt for a real environment.

## Health

- `GET /actuator/health` must return `UP`.
- `GET /actuator/prometheus` shows metrics, including `http_server_requests_seconds_*`.
- Compose stack status: `podman-compose ps`.
- App logs: `podman logs ledger_app_1`.

## Start, stop, reset

| Task | Command |
|---|---|
| Start | `podman-compose up -d --build` |
| Stop | `podman-compose down` |
| Wipe all data | `podman-compose down`, then `up -d`. Postgres has no volume, so a new container starts empty |
| Clear traces | `podman-compose down`, then `: > .data/otel/traces.jsonl`, then `up -d` |

## Check the invariants

```bash
scripts/invariants.sh
```

Every value except `outbox_pending` must be 0. `outbox_pending` counts events not yet sent or not yet consumed.
It falls to 0 when the system is idle. The script waits for it (default 600 seconds, set `WAIT_SECONDS`).
A non-zero `unbalanced_transactions` is a severity-1 bug. Stop writes and investigate.

## Problems and fixes

**The read model is behind the ledger.**
Check `SELECT count(*) FROM outbox_events WHERE sent_at IS NULL`. If it grows, the relay is down or Kafka is down.
Check `podman logs ledger_app_1 | grep -i relay`. If the unsent count is 0 but the projection is behind,
the consumer is slow or stopped. Check the consumer group: `podman exec ledger_redpanda_1 rpk group describe balance-projection`.
The ledger itself stays correct. Only `/balance/projection` lags.

**A relay crashed or was restarted.**
No action. Unsent rows stay unsent. The next poll sends them. Some events may arrive twice.
The consumer drops a repeated event id, so balances stay right.

**A client sees 422 `IDEMPOTENCY_KEY_REUSED`.**
The client sent the same key with a different request. The client must use a new key for a new request.

**A client sees 409 `INSUFFICIENT_FUNDS`.**
Available balance (posted minus holds) minus the amount is below the account floor. This is a normal business answer.

**Deadlock errors in the log.**
They must not happen. All locks are taken payment first, then accounts in id order. If one appears, find the code path that locks in a different order.

**No spans in Jaeger.**
Check `podman logs ledger_otel-collector_1`. The agent exports to `http://otel-collector:4318`. Jaeger keeps traces in memory only.

**Spans are dropped under load.**
The agent log shows "BatchSpanProcessor dropped N spans". This is expected under a load test. Traces are sampled by loss, not by design.

## Rebuild the read model

The projection can be rebuilt from Kafka:

1. Stop the app.
2. `TRUNCATE balance_projection, processed_events;`
3. Reset the group offsets: `rpk group seek balance-projection --to start --topics ledger.events` (inside the Redpanda container; check `rpk group seek --help` for the exact flags).
4. Start the app and wait until `scripts/invariants.sh` shows `projection_vs_ledger_diffs=0`.

This was not tested end to end. The steps follow from the design.
Events older than the topic retention are gone, so a rebuild only works while the full history is in Kafka.

## Reconciliation

Send a CSV with the header `payment_id,amount_minor` to `POST /reconciliations` (`Content-Type: text/csv`, new Idempotency-Key).
Read the result at `GET /reconciliations/latest`. The report counts `matched`, `missing`, `mismatched` and `unexpected`
and lists the payment ids. Optional schedule: set `ledger.recon.settlement-file` and `ledger.recon.cron`.
