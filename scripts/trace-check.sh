#!/usr/bin/env bash
# Sends the demo flow (includes a capture), then asserts one trace spans HTTP, JDBC and Kafka.
set -euo pipefail
cd "$(dirname "$0")/.."
scripts/demo.sh > /dev/null
for _ in $(seq 1 30); do
  if python3 scripts/trace_check.py .data/otel/traces.jsonl; then exit 0; fi
  sleep 2
done
exit 1
