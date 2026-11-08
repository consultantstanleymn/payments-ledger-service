#!/usr/bin/env python3
"""Reads Collector file-exporter output (OTLP JSON lines).
Asserts that one trace id holds: an HTTP server span, a JDBC span, a Kafka producer span and a
Kafka consumer span. Exit code 0 on success, 1 on failure."""
import json
import sys
from collections import defaultdict

SERVER, CLIENT, PRODUCER, CONSUMER = 2, 3, 4, 5


def attrs(span):
    out = {}
    for a in span.get("attributes", []):
        v = a["value"]
        out[a["key"]] = next(iter(v.values())) if v else None
    return out


def spans(path):
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            for rs in json.loads(line).get("resourceSpans", []):
                for ss in rs.get("scopeSpans", []):
                    for sp in ss.get("spans", []):
                        yield sp


def classify(sp):
    a = attrs(sp)
    kind = sp.get("kind")
    found = set()
    if kind == SERVER and ("http.request.method" in a or "http.method" in a):
        found.add("http-server")
    if kind == CLIENT and ("db.system" in a or "db.system.name" in a):
        found.add("jdbc")
    if kind == PRODUCER and ("messaging.system" in a):
        found.add("kafka-producer")
    if kind == CONSUMER and ("messaging.system" in a):
        found.add("kafka-consumer")
    return found


def main(path):
    traces = defaultdict(set)
    for sp in spans(path):
        traces[sp["traceId"]] |= classify(sp)
    need = {"http-server", "jdbc", "kafka-producer", "kafka-consumer"}
    for tid, have in traces.items():
        if need <= have:
            print(f"OK trace {tid} contains {sorted(have)}")
            return 0
    print("FAIL no trace has all of", sorted(need))
    for tid, have in traces.items():
        print(" ", tid, sorted(have))
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else ".data/otel/traces.jsonl"))
