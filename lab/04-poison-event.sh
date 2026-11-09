#!/usr/bin/env bash
# Scenario 4: a malformed event arrives on the event topic. It must go to the DLT, not block others.
set -u
# shellcheck source=lab/lib.sh disable=SC1091
. "$(dirname "$0")/lib.sh"
trap lab_down EXIT
lab_up || exit 1
load_bg 60
sleep 15
snapshot "before"
say "--- injection: produce one malformed record to ledger.events"
echo '{"this is not valid json' | podman exec -i "$RP" rpk topic produce ledger.events -k lab-bad >/dev/null
load_wait
sleep 5
say "DLT high watermark after the poison event: $(topic_hwm ledger.events.DLT)"
invariants "after poison event"
say "--- redrive: the malformed record cannot be fixed by a repeat, so it fails again"
curl -s -X POST -H 'X-Admin-Key: lab-admin-local-only' "$BASE/admin/dlt/redrive?max=10" | tee -a "$LAB_OUT"; echo | tee -a "$LAB_OUT"
sleep 8
say "DLT high watermark after redrive: $(topic_hwm ledger.events.DLT)"
say "Decision: a truly malformed record stays in the DLT. A person reviews it. See the runbook."
invariants "after redrive"
snapshot "after"
