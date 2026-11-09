#!/usr/bin/env python3
"""Check a Grafana dashboard JSON: title, uid, panels, targets with expr, known metrics only."""
import json
import re
import sys

KNOWN = {
    "http_server_requests_seconds_count",
    "http_server_requests_seconds_bucket",
    "ledger_projection_lag_seconds_bucket",
    "ledger_projection_lag_seconds_count",
}
RECORDED = re.compile(r"ledger:transfer_(availability|latency|freshness):ratio_rate\d+[mh]")


def check(path: str) -> list[str]:
    errors: list[str] = []
    with open(path, encoding="utf-8") as handle:
        dash = json.load(handle)
    for key in ("title", "uid"):
        if not dash.get(key):
            errors.append(f"missing {key}")
    panels = dash.get("panels") or []
    if not panels:
        errors.append("no panels")
    for panel in panels:
        if not panel.get("title") or not panel.get("type"):
            errors.append("panel without title or type")
        targets = panel.get("targets") or []
        if not targets:
            errors.append(f"panel {panel.get('title')}: no targets")
        for target in targets:
            expr = target.get("expr")
            if not expr:
                errors.append(f"panel {panel.get('title')}: target without expr")
                continue
            stripped = RECORDED.sub("", expr)
            for name in re.findall(r"[a-zA-Z_:][a-zA-Z0-9_:]*(?=\s*[{\[(]|\s*$|\s*[)/*+-])", stripped):
                if name.startswith(("ledger_", "http_server")) and name not in KNOWN:
                    errors.append(f"panel {panel.get('title')}: unknown metric {name}")
    return errors


if __name__ == "__main__":
    problems = check(sys.argv[1])
    for problem in problems:
        print("FAIL", problem)
    if not problems:
        print("OK dashboard is valid")
    sys.exit(1 if problems else 0)
