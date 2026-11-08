# ONBOARDING

This page gets a new developer from a clean machine to a green build in about 30 minutes.

## 1. Tools

| Tool | Version used | Note |
|---|---|---|
| JDK | 25 | Fedora: `sudo dnf install java-25-openjdk-devel`. CI uses Temurin 25 |
| Maven | 3.9 | The wrapper `./mvnw` downloads its own copy. Fedora package `maven` also works |
| podman and podman-compose | 5.x | Docker also works for the tests. The compose file is plain compose |
| k6 | 1.x | Only for the load test |
| python3 | 3.x | For `scripts/trace_check.py` |

## 2. First build

```bash
./mvnw -B verify -DskipITs
```

This runs the unit tests, the property tests and the Spotless format check. It needs no containers.
To fix formatting: `./mvnw spotless:apply`.

## 3. Integration tests

Testcontainers starts Postgres 16 and Redpanda. The first run pulls images, so it is slow.
With rootless podman:

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
./mvnw -B verify
```

Integration tests are named `*IT` and carry the JUnit tag `integration`.
Surefire skips that tag. Failsafe runs only that tag. On a 2015 laptop the whole `verify` takes about one minute after the images exist.

## 4. Local stack

```bash
./mvnw -B -DskipTests package
scripts/fetch-otel-agent.sh      # downloads the agent and checks its SHA-256
mkdir -p .data/otel
podman-compose up -d --build
curl -s http://127.0.0.1:18080/actuator/health
scripts/demo.sh
```

Ports (all on 127.0.0.1): app 18080, Postgres 18432, Redpanda Kafka API 18092, Collector OTLP/HTTP 18318, Jaeger UI 18686.
Stop it with `podman-compose down`.

## 5. Where things live

Read in this order:

1. `ledger/Rules.java`. Pure business rules. Start here.
2. `ledger/LedgerService.java`. Locks rows, calls a rule, writes the journal and the outbox.
3. `idem/IdempotentExecutor.java`. Wraps each write.
4. `outbox/OutboxRelay.java` and `projection/ProjectionConsumer.java`. The Kafka side.
5. `recon/ReconciliationService.java`.
6. `src/main/resources/db/migration/V1__ledger.sql`. Tables, checks and the zero-sum trigger.

## 6. Rules of the repo

- Money is a `long` in minor units. Never use `double` or `float`. A test enforces it in the `ledger` package.
- A rule never touches the database. The service never decides a business rule.
- A change to a table needs a new Flyway file. Never edit `V1`.
- Format with Spotless before you commit.
- No secrets in the repo. See SECURITY.md.
