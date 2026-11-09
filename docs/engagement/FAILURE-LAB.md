# Failure lab

Five scripted faults on the local stack. Each script starts the stack, adds load with k6 (3 virtual users), injects the fault, recovers, and runs `scripts/invariants.sh`. Run one with `flock heavy.lock nice -n 10 lab/<script>.sh`.

| Scenario | Injection | Expected | Observed | User impact | Invariants | SLO impact | Evidence | Follow-up |
|---|---|---|---|---|---|---|---|---|
| 1 Kafka down | Stop Redpanda for 120 s | API keeps accepting writes. Outbox grows. Projection catches up after the restart. | Unsent outbox rows grew from 36 to 31906. A write returned HTTP 201. All rows drained after restart. | Balances read from the projection are stale during the outage. Writes work. | Pass before and after | Freshness SLO burns. Availability SLO does not. | `evidence/lab-01-kafka-down.txt` | Alert on unsent outbox count. |
| 2 Postgres restart | `podman restart` of Postgres under load | The app recovers. No half transaction. | Health returned UP. Failure rate over the whole run was 0.04 percent. No transaction without entries. | A few requests fail during the restart. | Pass | A small availability burn. | `evidence/lab-02-postgres-restart.txt` | Retry guidance for clients. |
| 3 Relay kill | SIGKILL of the app during a burst | Duplicates on the topic are allowed. The projection counts each event once. | Topic records equal processed events (18716). | Requests fail while the app is down. | Pass | Availability burn while down. | `evidence/lab-03-relay-kill.txt` | Run two app instances in a real deployment. |
| 4 Poison event | One malformed record on the event topic | The record goes to the DLT. Other events continue. | DLT held 1 record. Redrive read 1 and the record failed again (DLT held 2). | None for valid events. | Pass | None. | `evidence/lab-04-poison-event.txt` | A person reviews malformed records (see runbook). |
| 5 Postgres latency | 200 ms latency toxic on the Postgres proxy | Latency SLO breaks. No data loss. | p95 was 37 ms before and about 2.4 s with the toxic. | Slow transfers. | Pass | Latency SLO burns fast. | `evidence/lab-05-pg-latency.txt` | Add a Postgres timeout budget. |

## Findings

Scenario 1. The outbox pattern works as designed. The ledger stays correct while Kafka is down. The cost is stale projections. The relay waits up to 30 s per send, so recovery takes some seconds after Redpanda returns.

Scenario 2. The pool reconnects without a restart of the app. The short failure window is visible in the k6 failure rate.

Scenario 3. The kill leaves rows in the outbox without a sent mark. The relay sends them again. The `processed_events` table removes the repeats.

Scenario 4. A malformed event does not block the partition. A repeat cannot fix a malformed record, so the DLT keeps it. This is a manual decision.

Scenario 5. A first version of this script mixed fast and slow periods and showed a p95 of 38 ms. The fast period sent many more requests and hid the slow ones. The script now measures only while the toxic is active.

## Limits

Redpanda runs as one node. All runs are short and happen on one laptop. The fault durations are not production values. The lab does not test multi-broker behavior or network partitions between brokers.
