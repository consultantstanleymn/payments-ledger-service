# Test Strategy

## Purpose and scope

The QA suite verifies the running payments ledger service from outside the process.
It uses black-box API calls, BDD scenarios, SQL checks, UI tests, and load tests.
The scope is a local compose stack on one laptop and the CI job for the API profile.
The QA suite does not change the service build or its existing tests.

## Test pyramid for this repo

The repository has these test layers:
- Unit and jqwik property tests in the service build.
- Testcontainers integration tests in the service build. The service build runs 37 integration tests.
- Black-box API tests with REST Assured and JSON Schema. The QA suite runs 15 API tests.
- Cucumber BDD scenarios. The QA suite runs 16 scenarios across 5 features.
- Ops paging API tests. The QA suite runs 5 ops paging tests.
- Selenium UI tests with page objects. The QA suite runs 6 UI tests in a Chromium container.
- Playwright console tests with axe. The console runs 13 mocked API tests and 1 live test.
- k6 SLO gate.
- JMeter load plan.

The lower layers run often and give fast feedback.
The upper layers run less often and prove the full system.

## What runs when

Each commit runs:
- Service unit and property tests.
- Service Testcontainers integration tests.
- Console lint, typecheck, unit tests, build, and Playwright tests.
- Black-box API and BDD suite in CI after the other jobs.

Nightly or on demand locally:
- Selenium UI suite.
- JMeter load plan.
- k6 SLO gate.

Before a release:
- All test layers.
- Run the full plan against a stack built from the release image.

## Test data strategy

Each test JVM gets a UUID called run id.
Every test creates its own accounts named `qa-<runId>-<n>`.
Writes go only through the public API.
The QA database connection is read-only.
The ledger is append-only, so there is no cleanup.
Tests never read or change rows they did not create.
Parallel test runs stay isolated because the names include the run id.

## Negative and boundary test design

The API tests include invalid payloads, missing fields, wrong API keys, and duplicate idempotency keys.
Boundary tests cover zero amounts, negative amounts, very large amounts, and long reference values.
The UI tests cover empty fields, failed sign-in, and duplicate account names.
The BDD scenarios cover expected business failures such as overdraft and duplicate payments.
Each negative test asserts the exact HTTP status and the JSON error schema.

## Flaky-test policy

Surefire reruns a failing test once.
A test that passes on the rerun is still reported as flaky in `target/flaky-report.md`.
The team fixes or quarantines a flaky test within one week.
The service build never uses silent retries.
Flaky tests are visible, not hidden.

## Entry and exit criteria for a release

Entry criteria:
- The release image is built.
- Unit, property, integration, API, BDD, UI, Playwright, k6, and JMeter results are available.
- No critical or high severity bug is open for the release scope.

Exit criteria:
- Unit and property tests pass.
- Service integration tests pass.
- Black-box API and BDD tests pass.
- Selenium UI and Playwright tests pass.
- k6 SLO thresholds pass.
- JMeter error rate is under 1 percent and p95 per sampler is under 300 ms on the local laptop.
- The flaky report is reviewed and no new flaky tests remain unassigned.

## What is not tested and why

Multi-region deployment is not tested. The local compose stack runs one copy of each service.
A real Kafka cluster failure beyond the lab scripts is not tested. The stack uses a single Redpanda node.
Real browsers other than Chromium are not tested. The Selenium container uses Chromium only.
Load beyond one laptop is not tested. The JMeter plan uses 20 threads and local thresholds.
