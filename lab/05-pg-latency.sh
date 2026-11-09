#!/usr/bin/env bash
# Scenario 5: Postgres gets 200 ms extra latency through Toxiproxy. Check the latency SLO.
# The load runs only while the toxic is active. A run that mixes fast and slow periods
# hides the slow requests, because the fast period sends many more requests.
set -u
# shellcheck source=lab/lib.sh disable=SC1091
. "$(dirname "$0")/lib.sh"
trap lab_down EXIT
lab_up || exit 1
say "--- baseline load without a toxic (30 s)"
load_bg 30
load_wait
say "baseline k6 http_req_duration p(95) (ms): $(k6_metric http_req_duration 'p(95)')"
say "--- injection: latency toxic 200 ms on proxy pg, load for 45 s"
toxic_add pg lat latency '{"latency":200}'
load_bg 45
load_wait
p95=$(k6_metric http_req_duration "p(95)")
say "toxic k6 http_req_duration p(95) (ms): $p95"
say "toxic k6 http_req_failed rate: $(k6_metric http_req_failed value)"
say "SLO latency target: 99 percent of POST /transfers under 300 ms. A p95 above 300 ms means the latency alert expression would fire."
toxic_clear pg lat
say "--- toxic removed"
snapshot "after"
invariants "after toxic removed"
