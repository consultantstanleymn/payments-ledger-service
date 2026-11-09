# Architecture

Fictional customer. This file shows the parts added for the reliability work. The base design is in the README.

```mermaid
flowchart LR
  client([Client]) -->|X-Api-Key| audit[Caller audit filter]
  audit --> api[Ledger API]
  api --> pg[(Postgres)]
  pg --> relay[Outbox relay]
  relay -->|ledger-created-at header| rp[(Redpanda)]
  rp --> proj[Projection consumer]
  proj -->|lag timer| metrics[Micrometer]
  proj -->|poison| dlt[(ledger.events.DLT)]
  dlt --> redrive[Admin redrive]
  redrive --> rp
  metrics --> prom[Prometheus]
  prom --> graf[Grafana SLO dashboard]
  prom --> rules[Burn-rate alert rules]
  toxi[Toxiproxy] -.->|lab only| pg
```

Trust boundaries: the client crosses the API key check. The operator crosses the admin key check. Toxiproxy exists only in the lab profile.

| Component | Role |
|---|---|
| Caller audit filter | Writes one append-only row per call. Wraps the key filter. |
| Ledger API | Idempotent writes. Optional `externalRef` on payments. |
| Outbox relay | Publishes unsent rows. Adds the `ledger-created-at` header. |
| Projection consumer | Builds balances. Records `ledger.projection.lag`. Sends poison events to the DLT. |
| Admin redrive | Reads the DLT and publishes records again. Needs `X-Admin-Key`. |
| Prometheus rules | Recording rules and multi-window burn-rate alerts for three SLIs. |
| Grafana | Provisioned SLO dashboard. |
| Toxiproxy | Adds latency to Postgres in the failure lab. |
