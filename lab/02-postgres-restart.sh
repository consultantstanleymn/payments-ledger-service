#!/usr/bin/env bash
# Scenario 2: Postgres restarts under load. Checks recovery and that no half transaction remains.
set -u
# shellcheck source=lab/lib.sh disable=SC1091
. "$(dirname "$0")/lib.sh"
trap lab_down EXIT
lab_up || exit 1
load_bg 90
sleep 20
snapshot "before"
say "--- injection: restart $PG"
podman restart "$PG" >/dev/null
sleep 5
wait_health 60 && say "app health UP again after the restart"
load_wait
say "k6 http_req_failed rate during the whole run (restart window included): $(k6_metric http_req_failed value)"
say "k6 iterations: $(k6_metric iterations count)"
snapshot "after"
invariants "after restart"
