#!/usr/bin/env python3
"""Small gate for JMeter dashboard statistics.json.

Usage:
  jmeter_gate.py <statistics.json> [thresholds.json]

The script prints a table and exits 1 when the total error rate is at or
above errorRatePct, or when any required sampler p95 is at or above its
limit. pct2ResTime from the JMeter dashboard is treated as p95.
"""
import json
import sys
from pathlib import Path


def load_json(path):
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def _num(value, default=0.0):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def evaluate(stats, thresholds):
    error_limit = _num(thresholds.get("errorRatePct"), 1.0)
    p95_limits = thresholds.get("p95Ms", {})
    rows = []
    ok = True

    total = stats.get("Total")
    if total is None:
        rows.append(("Total", 0, 0.0, 0.0, "-", "MISS", "missing Total"))
        ok = False
    else:
        samples = int(_num(total.get("sampleCount", 0)))
        error_pct = _num(total.get("errorPct", 0.0))
        p95 = _num(total.get("pct2ResTime", 0.0))
        if error_pct >= error_limit:
            ok = False
            status = "MISS"
        else:
            status = "PASS"
        rows.append(("Total", samples, error_pct, p95, error_limit, status, ""))

    for label, limit in sorted(p95_limits.items()):
        sample = stats.get(label)
        if sample is None:
            ok = False
            rows.append((label, 0, 0.0, 0.0, _num(limit), "MISS", "missing sampler"))
            continue

        samples = int(_num(sample.get("sampleCount", 0)))
        error_pct = _num(sample.get("errorPct", 0.0))
        p95 = _num(sample.get("pct2ResTime", 0.0))
        limit_num = _num(limit)
        if p95 >= limit_num:
            ok = False
            status = "MISS"
        else:
            status = "PASS"
        rows.append((label, samples, error_pct, p95, limit_num, status, ""))

    return ok, rows


def format_table(rows):
    lines = []
    lines.append(f"{'sampler':<20} {'samples':>8} {'errorPct':>10} {'p95 ms':>10} {'limit':>10} status")
    lines.append("-" * 76)
    for row in rows:
        label, samples, error_pct, p95, limit, status, _note = row
        if isinstance(limit, str):
            limit_s = limit
        else:
            limit_s = f"{limit:.1f}"
        lines.append(
            f"{label:<20} {samples:>8} {error_pct:>10.3f} {p95:>10.1f} {limit_s:>10} {status}"
        )
    return "\n".join(lines)


def main(argv=None):
    if argv is None:
        argv = sys.argv[1:]

    if not argv:
        print(__doc__, file=sys.stderr)
        return 2

    stats_path = Path(argv[0])
    if len(argv) > 1:
        thresholds_path = Path(argv[1])
    else:
        thresholds_path = Path(__file__).resolve().parents[1] / "perf" / "thresholds.json"

    stats = load_json(stats_path)
    thresholds = load_json(thresholds_path)
    ok, rows = evaluate(stats, thresholds)

    print(format_table(rows))
    if ok:
        print("gate status: PASS")
        return 0

    print("gate status: MISSED")
    return 1


if __name__ == "__main__":
    sys.exit(main())
