#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "${script_dir}/../.." && pwd)"
qa_dir="${root_dir}/qa-suite"
mvn="${root_dir}/mvnw"

with_stack=false
mode="api"
selenium_started=false
selenium_image="docker.io/selenium/standalone-chromium:4.52.0-20261101"

usage() {
  echo "usage: qa-run.sh [api|ui|perf|all] [--with-stack]"
}

for arg in "$@"; do
  case "$arg" in
    api|ui|perf|all) mode="$arg" ;;
    --with-stack) with_stack=true ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown argument: ${arg}" >&2
      usage >&2
      exit 2
      ;;
  esac
done

cleanup() {
  if [[ "${selenium_started}" == "true" ]]; then
    podman rm -f ledger-qa-selenium >/dev/null 2>&1 || true
  fi
  if [[ "${with_stack}" == "true" ]]; then
    ( cd "${root_dir}" && podman-compose down )
  fi
}
trap cleanup EXIT

if [[ "${with_stack}" == "true" ]]; then
  ( cd "${root_dir}" && podman-compose up -d --build )
fi

run_profile() {
  local profile="$1"
  if [[ "${profile}" == "perf" ]]; then
    "${mvn}" -B -f "${qa_dir}/pom.xml" verify -P"${profile}"
  else
    "${mvn}" -B -f "${qa_dir}/pom.xml" test -P"${profile}"
  fi
}

wait_for_selenium() {
  local base="$1"
  local status_url="${base%/}/status"
  local attempt=0
  until curl -fsS "${status_url}" | grep -Eq '"ready"[[:space:]]*:[[:space:]]*true'; do
    attempt=$((attempt + 1))
    if [[ "${attempt}" -ge 60 ]]; then
      echo "selenium did not become ready at ${status_url}" >&2
      return 1
    fi
    sleep 1
  done
}

start_selenium() {
  local base="${SELENIUM_URL:-http://127.0.0.1:26744}"
  base="${base%/}"
  podman rm -f ledger-qa-selenium >/dev/null 2>&1 || true
  podman run --pull=never -d --name ledger-qa-selenium --network host --shm-size 1g \
    -e SE_OPTS="--port 26744" \
    -e SE_START_VNC=false \
    "${selenium_image}" >/dev/null
  selenium_started=true
  echo "waiting for selenium at ${base}/status"
  if ! wait_for_selenium "${base}"; then
    stop_selenium
    return 1
  fi
}

stop_selenium() {
  if [[ "${selenium_started}" == "true" ]]; then
    podman rm -f ledger-qa-selenium >/dev/null 2>&1 || true
    selenium_started=false
  fi
}

run_profile_ui() {
  if ! start_selenium; then
    return 1
  fi

  local rc=0
  run_profile ui || rc=$?
  if [[ "${rc}" -ne 0 ]]; then
    stop_selenium
    return "${rc}"
  fi

  stop_selenium
}

status=0
case "${mode}" in
  api) run_profile api || status=1 ;;
  ui) run_profile_ui || status=1 ;;
  perf) run_profile perf || status=1 ;;
  all)
    run_profile api || status=1
    run_profile_ui || status=1
    run_profile perf || status=1
    ;;
esac

if [[ "${mode}" != "perf" ]]; then
  python3 "${script_dir}/flaky_report.py" \
    --reports "${qa_dir}/target/surefire-reports" \
    --output "${qa_dir}/target/flaky-report.md" || status=1
fi

echo
echo "reports:"
echo "  surefire:   ${qa_dir}/target/surefire-reports"
echo "  flaky:      ${qa_dir}/target/flaky-report.md"
echo "  jmeter:     ${qa_dir}/target/jmeter/results"
echo "  statistics: ${qa_dir}/target/jmeter/reports/ledger-payments/statistics.json"

if [[ "${status}" -ne 0 ]]; then
  exit "${status}"
fi
