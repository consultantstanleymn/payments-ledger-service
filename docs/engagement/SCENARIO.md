Fictional customer. Any match with a real company is not intended.

## Customer

CraftMarketCo runs a crafts marketplace. Buyers pay for items before sellers ship. CraftMarketCo holds the funds and pays out about 12,000 sellers each week.

## Money path

The ledger service records payment acceptance, journal entries, and seller balances. Payment acceptance starts at `POST /transfers`. A valid transfer creates idempotent records, journal entries, an outbox event, and a projection update.

## Finance question

Finance asks: "When a payment is accepted, how fast is it in the seller balance, and what happens when Kafka or Postgres fails?"

## Scope

The engagement covers reliability work for that money path:

- define service level indicators and error budgets
- build dashboards and alert rules
- run failure labs for Kafka and Postgres
- handle poison events and redrive
- prove rollout and rollback with image digests
- keep k6 smoke thresholds aligned to SLOs
- audit caller traffic
- document threats and streaming decisions
