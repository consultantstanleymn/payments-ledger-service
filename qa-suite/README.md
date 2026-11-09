# Ledger QA suite

Black-box QA suite for the payments ledger service. It is its own Maven project and it never
changes the service build. It tests the running stack from outside.

## Prerequisites

- Java 25 and the root Maven wrapper.
- The local stack from the repository root (`podman-compose up -d --build`).
- For UI tests, the Selenium container in the section below.
- For perf tests, the `perf` profile with a plan under `qa-suite/perf`.

## Run a profile

Use the run script from the repository root. It prints the report paths at the end.

```bash
qa-suite/scripts/qa-run.sh api    # REST Assured and Cucumber, tags not @ui
qa-suite/scripts/qa-run.sh ui     # Selenium and Cucumber, tag @ui
qa-suite/scripts/qa-run.sh perf   # JMeter plan
qa-suite/scripts/qa-run.sh all    # api, then ui, then perf
```

Add `--with-stack` to start the compose stack first and stop it on exit.

```bash
qa-suite/scripts/qa-run.sh api --with-stack
```

You can also call Maven directly with the root wrapper.

```bash
./mvnw -B -f qa-suite/pom.xml test -Papi
./mvnw -B -f qa-suite/pom.xml test -Pui
./mvnw -B -f qa-suite/pom.xml verify -Pperf
```

## Target system settings

Every setting comes from the environment. These are the local compose values only.

| Variable | Default |
|---|---|
| `LEDGER_URL` | `http://127.0.0.1:18080` |
| `LEDGER_API_KEY` | `dev-key-local-only` |
| `LEDGER_ADMIN_KEY` | empty, so admin routes answer 403 |
| `LEDGER_DB_URL` | `jdbc:postgresql://127.0.0.1:18432/ledger` |
| `LEDGER_DB_USER` | `ledger` |
| `LEDGER_DB_PASSWORD` | `ledger` |
| `SELENIUM_URL` | `http://127.0.0.1:26744` |
| `CONSOLE_URL` | `http://127.0.0.1:18080/console/` |

The database credentials are for the local compose stack. Do not use them anywhere else.

## Selenium container

The UI tests use a pinned Selenium Chromium container. The container uses host networking so the
browser can reach the local stack.

```bash
podman run -d --name ledger-qa-selenium --network host --shm-size 1g \
  -e SE_OPTS="--port 26744" -e SE_START_VNC=false \
  docker.io/selenium/standalone-chromium:<pinned-4.x-tag>
```

Check the open ports with `ss -ltn` and write them to the evidence file. Stop the container with
`podman rm -f ledger-qa-selenium`.

## Test data

Every test makes its own accounts with names `qa-<runId>-<n>`. `runId` is one UUID per JVM. Tests
never read or change rows they did not create, so parallel runs stay safe. The ledger is
append-only, so there is no cleanup.

## Reports

- Surefire XML: `qa-suite/target/surefire-reports`
- Flaky report: `qa-suite/target/flaky-report.md`
- JMeter results: `qa-suite/target/jmeter/results`
- JMeter statistics: `qa-suite/target/jmeter/results/statistics.json`
