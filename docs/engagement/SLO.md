# Service level objectives

Targets for a fictional customer. No production data. CraftMarketCo asks how fast an accepted payment reaches the seller balance.

## Service level indicators

| SLI | Target | Window | Query |
|---|---|---|---|
| Availability of valid transfer requests | 99.5 percent | 28 days | see below |
| Latency of `POST /transfers` under 300 ms | 99 percent | 28 days | see below |
| Projection freshness under 5 s | 99 percent | 28 days | see below |

4xx answers are invalid requests. They do not count as failures.

### Availability

```
sum(rate(http_server_requests_seconds_count{uri="/transfers",method="POST",status!~"5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count{uri="/transfers",method="POST",status!~"4.."}[5m]))
```

### Latency

```
sum(rate(http_server_requests_seconds_bucket{uri="/transfers",method="POST",le="0.3"}[5m]))
/
sum(rate(http_server_requests_seconds_count{uri="/transfers",method="POST"}[5m]))
```

### Freshness

```
sum(rate(ledger_projection_lag_seconds_bucket{le="5.0"}[5m]))
/
sum(rate(ledger_projection_lag_seconds_count[5m]))
```

## Why these targets

Sellers see their balance in the app. A slow transfer blocks a checkout, so latency has a tight bound. A stale balance causes support calls but no money loss, so 99 percent fits. The ledger itself stays correct when Kafka is down, because the outbox holds the events.

## Error budget policy

The error budget is the share of requests that may miss the target in 28 days. For 99.5 percent it is 0.5 percent.

| Budget spent | Action |
|---|---|
| 50 percent | The owner reviews the cause. Risky changes need a second review. |
| 75 percent | Freeze risky changes. Reliability work goes first. |
| 100 percent | Freeze all feature work except fixes. The owner reviews and sets a plan before the freeze ends. |

Burn-rate alerts (`observability/rules/slo.rules.yml`) warn before the budget runs out. The fast alert uses factor 14.4 on 1 h and 5 m windows. The slow alert uses factor 6 on 6 h and 30 m windows.
