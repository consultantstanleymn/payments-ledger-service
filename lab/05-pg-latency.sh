#!/usr/bin/env bash
# Scenario 5: Postgres gets 200 ms extra latency through Toxiproxy. Check the latency SLO.
set -u
# shellcheck source=lab/lib.sh disable=SC1091
. "$(dirname "$0")/lib.sh"
trap lab_down EXIT
lab_up || exit 1
load_bg 75
sleep 12
say "--- injection: latency toxic 200 ms on proxy pg"
toxic_add pg lat latency '{"latency":200}'
sleep 50
toxic_clear pg lat
say "--- toxic removed"
load_wait
p95=$(k6_metric http_req_duration "p(95)")
say "k6 http_req_duration p(95) over the whole run (ms): $p95"
say "k6 http_req_failed rate: $(k6_metric http_req_failed value)"
say "SLO latency target is 300 ms at p95 share 99 percent. p95 above 300 ms means the latency alert expression would fire."
snapshot "after"
invariants "after toxic removed"
