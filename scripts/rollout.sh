#!/usr/bin/env bash
# Rollout and rollback by image digest across an expand and contract migration.
#   v1 = the image before migrations V2 and V3, v2 = this commit.
# Steps: build both, start v1, roll forward to v2, roll back to v1 on the same database.
# Usage (HEAVY): flock heavy.lock nice -n 10 scripts/rollout.sh | tee out.txt
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1
IMG=localhost/payments-ledger-service
NET=ledger_default
NAME=ledger-app-roll
WT=/tmp/ledger-rollout-v1
BASE=http://127.0.0.1:18080
V1_SHA="${V1_SHA:-$(git rev-parse "$(git log --format=%H --grep='external reference to payments (expand step)' -1)^")}"
KEY=dev-key-local-only
export CONTAINER=ledger_postgres_1

step() { echo; echo "=== $*"; }
health() {
  for _ in $(seq 1 60); do
    if curl -sf "$BASE/actuator/health" >/dev/null 2>&1; then curl -s "$BASE/actuator/health"; echo; return 0; fi
    sleep 2
  done
  echo "health check timed out"; return 1
}
run_app() { # run_app <image-by-digest>
  podman rm -f "$NAME" >/dev/null 2>&1 || true
  podman run -d --name "$NAME" --network "$NET" -p 127.0.0.1:18080:8080 \
    -e DB_URL=jdbc:postgresql://postgres:5432/ledger -e KAFKA_BOOTSTRAP=redpanda:9092 -e PORT=8080 \
    -e OTEL_SDK_DISABLED=true -e 'SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS=*:future' \
    "$1" >/dev/null
}
load() { nice -n 10 k6 run -e VUS=3 -e DURATION=30s load/ledger.js 2>&1 | grep -E '^ *(iterations|http_req_failed|http_req_duration|invariant_violations)\.' ; }
api() { # api <path> <idem-key> <json>
  curl -s -X POST "$BASE$1" -H "X-Api-Key: $KEY" -H 'Content-Type: application/json' -H "Idempotency-Key: $2" -d "$3"
}
make_payment() { # make_payment <label> [externalRef]
  local run bank a b ref=""
  run="$1-$(date +%s%N)"
  bank=$(api /accounts "bank-$run" '{"name":"bank","currency":"USD","floorMinor":-1000000000}' | jq -r .accountId)
  a=$(api /accounts "a-$run" '{"name":"a","currency":"USD"}' | jq -r .accountId)
  b=$(api /accounts "b-$run" '{"name":"b","currency":"USD"}' | jq -r .accountId)
  api /transfers "f-$run" "{\"fromAccountId\":\"$bank\",\"toAccountId\":\"$a\",\"amountMinor\":10000}" >/dev/null
  [ -n "${2:-}" ] && ref=",\"externalRef\":\"$2\""
  api /payments "p-$run" "{\"payerAccountId\":\"$a\",\"payeeAccountId\":\"$b\",\"amountMinor\":500$ref}" | jq -r .id
}
cleanup() {
  podman rm -f "$NAME" >/dev/null 2>&1 || true
  [ -d "$WT" ] && git worktree remove --force "$WT" >/dev/null 2>&1
  podman-compose down >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "uptime start: $(uptime)"
step "1. build v1 from $V1_SHA (before V2 and V3)"
git worktree add "$WT" "$V1_SHA" >/dev/null 2>&1 || { echo "worktree failed"; exit 1; }
(cd "$WT" && ./mvnw -B -q -DskipTests -Dspotless.check.skip=true package >/dev/null 2>&1 && cp "$ROOT/otel/opentelemetry-javaagent.jar" otel/ && podman build -q -t "$IMG:v1" . >/dev/null) || { echo "v1 build failed"; exit 1; }
step "2. build v2 from HEAD $(git rev-parse --short HEAD)"
./mvnw -B -q -DskipTests -Dspotless.check.skip=true package >/dev/null 2>&1
podman build -q -t "$IMG:v2" . >/dev/null
D1=$(podman image inspect --format '{{.Digest}}' "$IMG:v1")
D2=$(podman image inspect --format '{{.Digest}}' "$IMG:v2")
step "3. digests"
echo "v1 digest: $D1"
echo "v2 digest: $D2"

step "4. start postgres and redpanda, run v1 by digest"
podman-compose up -d postgres redpanda >/dev/null 2>&1
for _ in $(seq 1 60); do podman exec "$CONTAINER" pg_isready -U ledger -d ledger >/dev/null 2>&1 && break; sleep 2; done
sleep 15
run_app "$IMG@$D1"
health || exit 1
load
scripts/invariants.sh; echo "invariants v1 exit=$?"
P1=$(make_payment v1)
echo "v1 payment: $P1"

step "5. roll forward: stop v1, run v2 by digest (applies V2 and V3)"
podman stop "$NAME" >/dev/null
run_app "$IMG@$D2"
health || exit 1
echo "applied migrations: $(podman exec -i "$CONTAINER" psql -U ledger -d ledger -At -c "SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history")"
P2=$(make_payment v2 "rollout-ref-1")
echo "v2 payment with externalRef: $P2"
curl -s -H "X-Api-Key: $KEY" "$BASE/payments?externalRef=rollout-ref-1" | jq -c '{id,externalRef}'
load
scripts/invariants.sh; echo "invariants v2 exit=$?"

step "6. roll back: stop v2, run v1 by digest on the same database (V2 and V3 stay applied)"
podman stop "$NAME" >/dev/null
run_app "$IMG@$D1"
health || exit 1
echo "applied migrations after rollback: $(podman exec -i "$CONTAINER" psql -U ledger -d ledger -At -c "SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history")"
load
P3=$(make_payment v1-again)
echo "v1 payment after rollback: $P3"
scripts/invariants.sh; echo "invariants after rollback exit=$?"
echo "external_ref of the v1 payment after rollback (empty means NULL): [$(podman exec -i "$CONTAINER" psql -U ledger -d ledger -At -c "SELECT external_ref FROM payments WHERE id = '$P3'")]"
echo "v2 data still readable by SQL: $(podman exec -i "$CONTAINER" psql -U ledger -d ledger -At -c "SELECT external_ref FROM payments WHERE id = '$P2'")"
echo "uptime end: $(uptime)"
