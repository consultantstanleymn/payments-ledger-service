#!/usr/bin/env bash
# Walks one payment through the API. Needs the compose stack. Prints every response.
set -euo pipefail
BASE="${BASE:-http://127.0.0.1:18080}"
KEY="${API_KEY:-dev-key-local-only}"
RUN="$(date +%s%N)"

post() { # path idempotency-key body
  curl -sS -X POST "$BASE$1" -H "X-Api-Key: $KEY" -H "Idempotency-Key: $2" \
    -H 'Content-Type: application/json' -d "$3"
}
field() { python3 -c "import sys,json; print(json.load(sys.stdin)['$1'])"; }

echo "# create accounts"
BANK=$(post /accounts "bank-$RUN" '{"name":"bank","currency":"USD","floorMinor":-100000000}' | field accountId)
BUYER=$(post /accounts "buyer-$RUN" '{"name":"buyer","currency":"USD"}' | field accountId)
SELLER=$(post /accounts "seller-$RUN" '{"name":"seller","currency":"USD"}' | field accountId)
echo "bank=$BANK buyer=$BUYER seller=$SELLER"

echo "# fund the buyer with 100.00 USD"
post /transfers "fund-$RUN" "{\"fromAccountId\":\"$BANK\",\"toAccountId\":\"$BUYER\",\"amountMinor\":10000}"; echo

echo "# authorize 40.00 (hold)"
PAY=$(post /payments "auth-$RUN" "{\"payerAccountId\":\"$BUYER\",\"payeeAccountId\":\"$SELLER\",\"amountMinor\":4000}" | tee /dev/stderr | field id)
echo
echo "# buyer balance with hold"
curl -sS "$BASE/accounts/$BUYER/balance" -H "X-Api-Key: $KEY"; echo

echo "# capture 35.00"
post "/payments/$PAY/capture" "cap-$RUN" '{"amountMinor":3500}'; echo

echo "# replay the same capture request: same body, header Idempotent-Replay"
curl -sS -i -X POST "$BASE/payments/$PAY/capture" -H "X-Api-Key: $KEY" -H "Idempotency-Key: cap-$RUN" \
  -H 'Content-Type: application/json' -d '{"amountMinor":3500}' | grep -iE '^(HTTP|idempotent-replay)|^\{'

echo "# same key, different body: 422"
curl -sS -w ' [HTTP %{http_code}]\n' -X POST "$BASE/payments/$PAY/capture" -H "X-Api-Key: $KEY" \
  -H "Idempotency-Key: cap-$RUN" -H 'Content-Type: application/json' -d '{"amountMinor":1}'

echo "# refund 5.00"
post "/payments/$PAY/refund" "ref-$RUN" '{"amountMinor":500}'; echo

sleep 2
echo "# ledger balances"
for a in $BUYER $SELLER; do curl -sS "$BASE/accounts/$a/balance" -H "X-Api-Key: $KEY"; echo; done
echo "# Kafka read model balances (built by the consumer)"
for a in $BUYER $SELLER; do curl -sS "$BASE/accounts/$a/balance/projection" -H "X-Api-Key: $KEY"; echo; done
