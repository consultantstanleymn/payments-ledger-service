# Review guide (60 minutes)

Fictional customer: CraftMarketCo. Question: "When a payment is accepted, how fast is it in the seller balance, and what happens when Kafka or Postgres fails?"

## 5-minute pitch

The ledger writes balanced journal rows and an outbox row in one transaction. A relay sends events to Kafka. A projection builds balances. This work adds three SLOs with burn-rate alerts, a failure lab with five faults, a dead-letter topic with redrive, rollback by image digest, and an append-only caller audit log. Every claim has a command and an evidence file.

## Demo steps

| Minute | Step | Command |
|---|---|---|
| 5 to 15 | Alert rules fire and stay quiet | `podman run --rm -v "$PWD/observability/rules:/r:ro,z" --entrypoint promtool docker.io/prom/prometheus:v2.55.1 test rules /r/slo.rules.test.yml` |
| 15 to 25 | Kafka down, then recovery | `flock heavy.lock nice -n 10 lab/01-kafka-down.sh` (about 4 minutes) |
| 25 to 35 | Poison event, DLT, redrive | `./mvnw -B verify -Dit.test=DeadLetterIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` |
| 35 to 45 | Roll forward and back by digest | `flock heavy.lock nice -n 10 scripts/rollout.sh` |
| 45 to 55 | SLO gate fails on slow database | see `evidence/k6-slo-gate.txt` |

## Likely questions

| # | Question | Short answer | Where |
|---|---|---|---|
| 1 | Why these three SLIs? | They match what finance asks: success, speed, freshness. | `SLO.md` |
| 2 | Why do 4xx not count as failures? | They are invalid requests, not service faults. | `SLO.md` |
| 3 | Why burn-rate alerts with two windows? | The long window shows real burn. The short window stops the alert quickly after recovery. | `observability/rules/slo.rules.yml` |
| 4 | How do you know the alerts work? | promtool tests: one firing and one quiet case for each. | `evidence/promtool-slo-rules.txt` |
| 5 | What did the dashboard first show? | No data for latency. The 300 ms bucket did not exist. Fixed and tested. | `MetricsIT`, `application.yml` |
| 6 | What if Kafka is down? | Writes continue. The outbox grows. The projection catches up later. | `evidence/lab-01-kafka-down.txt` |
| 7 | What if the app is killed mid-batch? | The relay sends the rows again. `processed_events` counts each once. | `evidence/lab-03-relay-kill.txt` |
| 8 | Why did lab 5 change? | A mixed run hid slow requests. It now measures only during the toxic. | `FAILURE-LAB.md` |
| 9 | What goes to the DLT? | Malformed events. Other events continue. | `KafkaConfig.java` |
| 10 | Is redrive safe to repeat? | Yes. The dedupe table stops double application. | `DeadLetterIT.redriveTwiceDoesNotDoubleApply` |
| 11 | Why can a malformed record not be redriven? | A repeat fails again. A person decides. | `evidence/lab-04-poison-event.txt` |
| 12 | Why is rollback safe after V3? | Both migrations only add. The old image ignores them. | `ROLLOUT.md` |
| 13 | When is rollback not safe? | After a NOT NULL or drop step. | `ROLLOUT.md` |
| 14 | What gap did the rollout show? | The old image lacks the Flyway ignore-future setting. Ship it one release early. | `ROLLOUT.md` |
| 15 | Why does the audit filter wrap the key filter? | To record rejected calls as anonymous. | `adr/0002-caller-audit-write-path.md` |
| 16 | What if the audit write fails? | The call succeeds. A counter shows it. | `ADR 0002`, `RUNBOOK.md` |
| 17 | Is the audit log tamper-proof? | No. A trigger blocks UPDATE and DELETE only. | `SECURITY.md` |
| 18 | Why keep the Kafka API? | Replay rebuilds the projection. | `adr/0003-streaming-choice.md` |
| 19 | Did CI run for this work? | No. New workflow steps are authored, not run. | README |
| 20 | What is not covered? | Multi-broker Kafka, real networks, production load. | `FAILURE-LAB.md` |

## Remaining risks

Static API keys (accepted, see `RISK-DECISION.md`). No rate limit. One-node Redpanda. Short lab runs on one laptop. Alerts proven on synthetic series only.
