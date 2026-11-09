#!/usr/bin/env bash
# Scenario 1: Kafka (Redpanda) is down for 2 minutes while transfers keep coming.
# Usage: LAB_OUT=<file> lab/01-kafka-down.sh   (HEAVY: wrap with the heavy lock and nice)
set -u
# shellcheck source=lab/lib.sh disable=SC1091
. "$(dirname "$0")/lib.sh"
trap lab_down EXIT
lab_up || exit 1
load_bg 170
sleep 15
snapshot "before"
say "--- injection: stop $RP for 120 s"
podman stop "$RP" >/dev/null
sleep 60
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/accounts" \
  -H 'X-Api-Key: dev-key-local-only' -H 'Content-Type: application/json' \
  -H "Idempotency-Key: lab-during-$(date +%s)" -d '{"name":"lab","currency":"USD"}')
say "API accepted a write while Kafka was down: HTTP $code"
sleep 60
snapshot "during"
say "--- recovery: start $RP"
podman start "$RP" >/dev/null
load_wait
snapshot "after (before drain check)"
invariants "after recovery"
snapshot "after"
