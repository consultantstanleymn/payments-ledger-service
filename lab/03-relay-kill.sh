#!/usr/bin/env bash
# Scenario 3: the app container is killed hard during a burst. Duplicates on the topic are allowed.
set -u
# shellcheck source=lab/lib.sh disable=SC1091
. "$(dirname "$0")/lib.sh"
trap lab_down EXIT
lab_up || exit 1
load_bg 90
sleep 25
snapshot "before"
say "--- injection: SIGKILL $APP during the burst"
podman kill --signal KILL "$APP" >/dev/null
sleep 5
if ! curl -sf "$BASE/actuator/health" >/dev/null 2>&1; then podman start "$APP" >/dev/null 2>&1 || true; fi
wait_health 90 && say "app health UP again after the kill"
load_wait
snapshot "after"
invariants "after kill"
sleep 2
say "topic ledger.events high watermark (all records, duplicates included): $(topic_hwm ledger.events)"
say "processed_events (each event counted once): $(psql_q "SELECT count(*) FROM processed_events")"
say "outbox_events total: $(psql_q "SELECT count(*) FROM outbox_events")"
