# Ledger QA suite

A black-box test suite that tests the running ledger from outside: BDD scenarios, API and contract tests, SQL checks, browser tests and a load plan.

## Why this suite is separate

The service build already has unit, property and Testcontainers integration tests. Those tests run inside the service Maven module and test components directly. This suite is its own Maven project under `qa-suite/` and exercises the running system only. It calls the API over HTTP, verifies Postgres through JDBC, drives a real browser and runs JMeter. It never changes the service code, the root `pom.xml` or anything under `src/`.

## Stack

Java 25, JUnit 5, Cucumber, REST Assured, JSON Schema, Selenium WebDriver with Chromium in a container, JDBC, JMeter.

## What it covers

| Layer | Tool | Count | Command |
|---|---|---|---|
| BDD scenarios | Cucumber | 5 features, 16 scenarios | `./mvnw -B -f qa-suite/pom.xml test -Papi` |
| API and contract tests | REST Assured, JSON Schema | 15 tests | `./mvnw -B -f qa-suite/pom.xml test -Papi` |
| Data layer checks | JDBC with the PostgreSQL driver | 4 tests | `./mvnw -B -f qa-suite/pom.xml test -Papi` |
| Ops paging tests | REST Assured | 5 tests | `./mvnw -B -f qa-suite/pom.xml test -Papi` |
| Stack health | REST Assured | 1 test | `./mvnw -B -f qa-suite/pom.xml test -Papi` |
| API suite total | JUnit Platform | 41 tests, 0 failures | `./mvnw -B -f qa-suite/pom.xml test -Papi` |
| UI tests | Selenium WebDriver with Chromium in a container | 6 tests | `./mvnw -B -f qa-suite/pom.xml test -Pui` |
| Load plan | JMeter | about 32,000 samples, 0 percent errors, p95 81 to 148 ms against a 300 ms limit | `./mvnw -B -f qa-suite/pom.xml verify -Pperf` |

## Test data

Every test creates its own accounts with names `qa-<runId>-<n>`. `runId` is one UUID per JVM. Tests never read or change rows they did not create, so parallel runs stay safe. The ledger is append-only, so there is no cleanup.

## Flaky policy

The run script writes `qa-suite/target/flaky-report.md` from Surefire XML. A test that fails only on a rerun without any code change is handled as described in the test strategy.

See [docs/engagement/TEST-STRATEGY.md](../docs/engagement/TEST-STRATEGY.md).

## Run it

Start the compose stack from the repository root:

```bash
podman-compose up -d --build
```

Run one profile or all profiles:

```bash
qa-suite/scripts/qa-run.sh api     # REST Assured and Cucumber, tags not @ui
qa-suite/scripts/qa-run.sh ui      # Selenium only, tag @ui
qa-suite/scripts/qa-run.sh perf    # JMeter only
qa-suite/scripts/qa-run.sh all     # api, then ui, then perf
```

To start the stack, run the selected profile, and stop the stack on exit:

```bash
qa-suite/scripts/qa-run.sh api --with-stack
```

Reports:

- Surefire XML: `qa-suite/target/surefire-reports`
- Cucumber report: `qa-suite/target/cucumber-report.html`
- Flaky report: `qa-suite/target/flaky-report.md`
- JMeter report: `qa-suite/target/jmeter/reports/ledger-payments`

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

The database credentials are for the local compose stack only. Do not use them anywhere else.

## Selenium container

The UI tests use a pinned Selenium Chromium container. It uses host networking so the browser can reach the local stack.

```bash
podman run -d --name ledger-qa-selenium --network host --shm-size 1g \
  -e SE_OPTS="--port 26744" -e SE_START_VNC=false \
  docker.io/selenium/standalone-chromium:<pinned-4.x-tag>
```

After it starts, check the open ports with `ss -ltn` and write the open ports into the evidence file. Stop the container with `podman rm -f ledger-qa-selenium`.

If the image ignores `SE_OPTS`, stop and report the problem. Do not fall back to a host chromedriver download.

## Verified locally

| Claim | Evidence |
|---|---|
| Full API run: 5 features, 16 scenarios; API and contract 15 tests; data layer 4 tests; ops paging 5 tests; stack health 1 test; total 41 tests, 0 failures, 0 errors | `docs/engagement/evidence/qa-full.txt` |
| UI run: 6 Selenium tests, 0 failures, 0 errors | `docs/engagement/evidence/qa-ui.txt` |
| JMeter gate: PASS, about 32,000 samples, 0 percent errors, p95 81 to 148 ms against a 300 ms limit | `docs/engagement/evidence/qa-jmeter.txt` |
| Flaky count: 0 | `docs/engagement/evidence/qa-full.txt` |

## Authored, not run

| File | What it would do | Why it did not run |
|---|---|---|
| `.github/workflows/ci.yml` job `qa-suite` | Run the QA suite on GitHub Actions | Authored, not run: this job is written and syntax checked with `actionlint`. It was not executed against GitHub Actions. Reason: needs GitHub Actions. |
