# ADR 0003: Streaming choice for ledger events

Status: accepted (decision level only)

## Context

The ledger publishes events after each change. A projection builds balance read models from them. The fictional customer pays out about 12,000 sellers each week. That is a low event rate. The key needs are order per account, replay to rebuild the projection, and simple operations.

## Options

1. Kafka API: Redpanda locally, a managed Kafka service (for example MSK) in a real setup.
2. SQS with SNS fan-out.
3. Kinesis Data Streams.

## Criteria

| Criterion | Kafka API | SQS and SNS | Kinesis |
|---|---|---|---|
| Order per account | Yes, by key within a partition | FIFO queues give order per group, with throughput limits | Yes, by partition key within a shard |
| Replay to rebuild a projection | Yes, within retention, from any offset | No. A consumed message is gone. Needs a separate archive | Yes, within the retention window |
| Many consumer groups | Yes | Yes, with one queue for each subscriber | Yes, with limits on shared read throughput |
| Exactly-once claim | None. The design uses at-least-once delivery and a dedupe table | None. Standard queues can deliver twice | None |
| Cost at about 12,000 payouts a week | Small, but a managed cluster has a base cost | Very low, pay per request | Low, but a shard has an hourly cost |
| Operations load | Highest for self-managed. Medium for managed | Lowest | Low to medium |

## Decision

Keep the Kafka API. The replay need decides it. The projection can be rebuilt from the log, and the runbook uses that. Both the dead-letter topic and the failure lab use Kafka behavior.

For a real deployment at this event rate, a managed Kafka service or Kinesis is a reasonable choice. SQS with SNS is the cheapest, but it loses replay unless an archive is added. This ADR does not choose a vendor.

## Consequences

- The dedupe table `processed_events` stays necessary, because no option gives exactly-once delivery.
- This ADR makes no benchmark claim. The repo ran only a single local Redpanda node.
- The repo did not test MSK or Kinesis.
