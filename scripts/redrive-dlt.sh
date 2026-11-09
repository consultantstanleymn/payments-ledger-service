#!/usr/bin/env bash
# Redrive up to N records from the dead-letter topic. Needs LEDGER_ADMIN_KEY in the app.
# Usage: LEDGER_ADMIN_KEY=... scripts/redrive-dlt.sh [max] [base-url]
set -euo pipefail
max="${1:-100}"
base="${2:-http://127.0.0.1:18080}"
: "${LEDGER_ADMIN_KEY:?set LEDGER_ADMIN_KEY}"
curl -fsS -X POST -H "X-Admin-Key: ${LEDGER_ADMIN_KEY}" "${base}/admin/dlt/redrive?max=${max}"
echo
