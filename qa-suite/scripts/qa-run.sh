#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "${script_dir}/../.." && pwd)"
qa_dir="${root_dir}/qa-suite"
mvn="${root_dir}/mvnw"

with_stack=false
mode="api"

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
  if [[ "${with_stack}" == "true" ]]; then
    ( cd "${root_dir}" && podman-compose down )
  fi
}

if [[ "${with_stack}" == "true" ]]; then
  ( cd "${root_dir}" && podman-compose up -d --build )
  trap cleanup EXIT
fi

run_profile() {
  local profile="$1"
  if [[ "${profile}" == "perf" ]]; then
    "${mvn}" -B -f "${qa_dir}/pom.xml" verify -P"${profile}"
  else
    "${mvn}" -B -f "${qa_dir}/pom.xml" test -P"${profile}"
  fi
}

status=0
case "${mode}" in
  api) run_profile api || status=1 ;;
  ui) run_profile ui || status=1 ;;
  perf) run_profile perf || status=1 ;;
  all)
    run_profile api || status=1
    run_profile ui || status=1
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
