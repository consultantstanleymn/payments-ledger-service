#!/usr/bin/env bash
# Downloads the OpenTelemetry Java agent from Maven Central and checks its SHA-256.
set -euo pipefail
VERSION=2.33.1
SHA256=e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149
DEST="$(dirname "$0")/../otel/opentelemetry-javaagent.jar"
if [ -f "$DEST" ] && echo "$SHA256  $DEST" | sha256sum -c --status; then exit 0; fi
curl -fsSL -o "$DEST" "https://repo.maven.apache.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/${VERSION}/opentelemetry-javaagent-${VERSION}.jar"
echo "$SHA256  $DEST" | sha256sum -c -
