#!/usr/bin/env bash
# Shared helpers for the failure lab. Source this file from a scenario script.
# Lab containers (exact names, from podman-compose project "ledger"):
#   ledger_postgres_1 ledger_redpanda_1 ledger_app_1 ledger_toxiproxy_1
set -u
LAB_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LAB_TMP="${LAB_TMP:-/tmp/ledger-lab}"
LAB_OUT="${LAB_OUT:-$LAB_TMP/out.txt}"
BASE="${BASE:-http://127.0.0.1:18080}"
TOXI="${TOXI:-http://127.0.0.1:25374}"
PG=ledger_postgres_1
RP=ledger_redpanda_1
export APP=ledger_app_1
mkdir -p "$LAB_TMP"

say() { echo "$*" | tee -a "$LAB_OUT"; }
dc() { (cd "$LAB_ROOT" && podman-compose -f compose.yaml -f compose.lab.yaml --profile lab "$@"); }
psql_q() { podman exec -i "$PG" psql -U ledger -d ledger -At -c "$1"; }

wait_health() {
  for _ in $(seq 1 "${1:-90}"); do
    curl -sf "$BASE/actuator/health" >/dev/null 2>&1 && return 0
    sleep 2
  done
  say "health check timed out"; return 1
}

toxic_api_ready() {
  for _ in $(seq 1 30); do curl -sf "$TOXI/version" >/dev/null 2>&1 && return 0; sleep 1; done
  return 1
}

lab_up() {
  : > "$LAB_OUT"
  say "uptime start: $(uptime)"
  dc up -d postgres redpanda toxiproxy >/dev/null 2>&1
  toxic_api_ready || { say "toxiproxy API not ready"; return 1; }
  curl -sf -X POST "$TOXI/proxies" -d '{"name":"pg","listen":"0.0.0.0:25375","upstream":"postgres:5432"}' >/dev/null || true
  curl -sf -X POST "$TOXI/proxies" -d '{"name":"kafka","listen":"0.0.0.0:25376","upstream":"redpanda:9092"}' >/dev/null || true
  for _ in $(seq 1 60); do
    podman exec "$PG" pg_isready -U ledger -d ledger >/dev/null 2>&1 && break; sleep 2
  done
  dc up -d app >/dev/null 2>&1
  wait_health 120
}

lab_down() {
  say "uptime end: $(uptime)"
  dc down >/dev/null 2>&1 || true
}

# load_bg <seconds>: k6 in the background with 3 virtual users.
load_bg() {
  (cd "$LAB_ROOT" && nice -n 10 k6 run -e VUS=3 -e "DURATION=${1}s" \
    --summary-export "$LAB_TMP/k6-summary.json" load/ledger.js > "$LAB_TMP/k6.log" 2>&1; \
    echo "k6 exit=$?" >> "$LAB_TMP/k6.log") &
  LOAD_PID=$!
}
load_wait() { wait "$LOAD_PID" 2>/dev/null || true; tail -1 "$LAB_TMP/k6.log" | tee -a "$LAB_OUT"; }

# k6_metric <metric> <field>
k6_metric() { jq -r ".metrics[\"$1\"][\"$2\"] // .metrics[\"$1\"].values[\"$2\"] // \"n/a\"" "$LAB_TMP/k6-summary.json"; }

topic_hwm() {
  podman exec "$RP" rpk topic describe "$1" -p 2>/dev/null | awk 'NR>1 && $1 ~ /^[0-9]+$/ {s+=$6} END {print s+0}'
}

snapshot() {
  say "--- snapshot: $1 ($(date -u +%H:%M:%S)Z)"
  say "outbox_unsent=$(psql_q "SELECT count(*) FROM outbox_events WHERE sent_at IS NULL")"
  say "outbox_total=$(psql_q "SELECT count(*) FROM outbox_events")"
  say "processed_events=$(psql_q "SELECT count(*) FROM processed_events")"
  say "account_posted_sum=$(psql_q "SELECT COALESCE(sum(posted_minor),0) FROM accounts")"
  say "journal_txn_without_entries=$(psql_q "SELECT count(*) FROM journal_transactions t WHERE NOT EXISTS (SELECT 1 FROM journal_entries e WHERE e.txn_id = t.id)" 2>/dev/null || echo n/a)"
}

invariants() {
  say "--- invariants: $1"
  (cd "$LAB_ROOT" && WAIT_SECONDS="${WAIT_SECONDS:-300}" scripts/invariants.sh) 2>&1 | tee -a "$LAB_OUT"
  say "invariants exit=${PIPESTATUS[0]}"
}

toxic_add() { # toxic_add <proxy> <name> <type> <json-attributes>
  curl -sf -X POST "$TOXI/proxies/$1/toxics" -d "{\"name\":\"$2\",\"type\":\"$3\",\"attributes\":$4}" >/dev/null
}
toxic_clear() { curl -sf -X DELETE "$TOXI/proxies/$1/toxics/$2" >/dev/null || true; }
