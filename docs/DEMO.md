# DEMO

Real output of `scripts/demo.sh` against the local stack, copied from the terminal on 2026-11-07.
Amounts are minor units: 10000 means 100.00 USD.

```
# create accounts
bank=5fee4618-f601-4cbd-83e8-f371e877b744 buyer=1d3cdf7a-f491-456e-89e8-42eb6a0cb134 seller=efcbf161-2756-4c3a-acbf-c02149a4c3d6
# fund the buyer with 100.00 USD
{"transactionId":"5a69451b-ae90-4873-bc16-8f20cf838aa6"}
# authorize 40.00 (hold)
{"id":"bb1dc66c-9486-4bbb-a56b-1cad71a286a7","payerId":"1d3cdf7a-f491-456e-89e8-42eb6a0cb134","payeeId":"efcbf161-2756-4c3a-acbf-c02149a4c3d6","currency":"USD","authorizedMinor":4000,"capturedMinor":0,"refundedMinor":0,"status":"AUTHORIZED"}
# buyer balance with hold
{"accountId":"1d3cdf7a-f491-456e-89e8-42eb6a0cb134","currency":"USD","floorMinor":0,"postedMinor":10000,"heldMinor":4000,"availableMinor":6000}
# capture 35.00
{"id":"bb1dc66c-9486-4bbb-a56b-1cad71a286a7","payerId":"1d3cdf7a-f491-456e-89e8-42eb6a0cb134","payeeId":"efcbf161-2756-4c3a-acbf-c02149a4c3d6","currency":"USD","authorizedMinor":4000,"capturedMinor":3500,"refundedMinor":0,"status":"CAPTURED"}
# replay the same capture request: same body, header Idempotent-Replay
HTTP/1.1 200 
Idempotent-Replay: true
{"id":"bb1dc66c-9486-4bbb-a56b-1cad71a286a7","payerId":"1d3cdf7a-f491-456e-89e8-42eb6a0cb134","payeeId":"efcbf161-2756-4c3a-acbf-c02149a4c3d6","currency":"USD","authorizedMinor":4000,"capturedMinor":3500,"refundedMinor":0,"status":"CAPTURED"}
# same key, different body: 422
{"code":"IDEMPOTENCY_KEY_REUSED","message":"key used with a different request"} [HTTP 422]
# refund 5.00
{"id":"bb1dc66c-9486-4bbb-a56b-1cad71a286a7","payerId":"1d3cdf7a-f491-456e-89e8-42eb6a0cb134","payeeId":"efcbf161-2756-4c3a-acbf-c02149a4c3d6","currency":"USD","authorizedMinor":4000,"capturedMinor":3500,"refundedMinor":500,"status":"CAPTURED"}
# ledger balances
{"accountId":"1d3cdf7a-f491-456e-89e8-42eb6a0cb134","currency":"USD","floorMinor":0,"postedMinor":7000,"heldMinor":0,"availableMinor":7000}
{"accountId":"efcbf161-2756-4c3a-acbf-c02149a4c3d6","currency":"USD","floorMinor":0,"postedMinor":3000,"heldMinor":0,"availableMinor":3000}
# Kafka read model balances (built by the consumer)
{"accountId":"1d3cdf7a-f491-456e-89e8-42eb6a0cb134","postedMinor":7000,"heldMinor":0,"eventCount":4}
{"accountId":"efcbf161-2756-4c3a-acbf-c02149a4c3d6","postedMinor":3000,"heldMinor":0,"eventCount":2}
```

What the output shows:

1. The buyer starts with 10000 posted. The authorization places a hold of 4000. Posted stays 10000, available drops to 6000.
2. Capture of 3500 moves money. The hold is released in full. The payer ends at 6500, the seller at 3500.
3. The same capture request with the same Idempotency-Key returns the stored body and the header `Idempotent-Replay: true`. No new journal entry is written.
4. The same key with another body returns 422 `IDEMPOTENCY_KEY_REUSED`.
5. A refund of 500 moves money back. Buyer 7000, seller 3000.
6. The read model built from Kafka (`eventCount` 4 and 2) shows the same posted balances as the ledger.

## Trace check

`scripts/trace-check.sh` sends the same flow and then reads the Collector file `.data/otel/traces.jsonl`.
Output:

```
OK trace a9947790bc3ecdf7b6f4d2bd388f6cd2 contains ['http-server', 'jdbc', 'kafka-consumer', 'kafka-producer']
```

The check also fails when it should. With a route name that does not exist, it prints `FAIL no trace has all of [...]`.
The Jaeger view of such a trace is in `docs/img/jaeger-trace.png`.
The Kafka consumer span in the batch listener is created by hand, see DECISIONS.md.

## Reconciliation

The fixture test in `ReconciliationIT` creates 100 captured payments, writes a settlement file with 98 matching rows,
1 row with an amount that is 1 minor unit higher, and 1 row left out. The report holds
`matched=98 missing=1 mismatched=1 unexpected=0`.
To run it by hand, send the CSV (`payment_id,amount_minor`) with `Content-Type: text/csv` to `POST /reconciliations`.
