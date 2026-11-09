#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PORTS=(18080 18432 18092 18318 18686)

for port in "${PORTS[@]}"; do
  if ss -ltnH "sport = :${port}" | grep -q .; then
    echo "Port ${port} is already in use." >&2
    exit 1
  fi
done

cleanup() {
  cd "$ROOT_DIR"
  podman-compose down || true
}
trap cleanup EXIT

npm --prefix console run build

./mvnw -B -q -DskipTests package

if [[ ! -f otel/opentelemetry-javaagent.jar ]]; then
  scripts/fetch-otel-agent.sh
fi

podman-compose up -d --build

echo "Waiting for health endpoint..."
health_ok=0
for ((i = 0; i < 120; i += 1)); do
  if curl -fsS --max-time 2 http://127.0.0.1:18080/actuator/health >/dev/null; then
    health_ok=1
    break
  fi
  sleep 1
done

if [[ "$health_ok" -ne 1 ]]; then
  echo "Health endpoint did not become ready within 120 seconds." >&2
  exit 1
fi

bash scripts/demo.sh >/dev/null 2>&1

(
  cd "$ROOT_DIR/console"
  LEDGER_URL=http://127.0.0.1:18080 npx playwright test -c playwright.live.config.ts
)

echo "CONSOLE LIVE OK"
