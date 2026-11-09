# SECURITY

This is a portfolio project. It is not hardened for production. This page lists what exists and what does not.

## What exists

- Every API route except `/actuator/*` and `/openapi.yaml` needs an `X-Api-Key` header. Unknown keys get 401.
- Each key maps to a client id. Idempotency keys are scoped to the client id.
- SQL uses bound parameters. No string concatenation of user input into SQL (table names in test code are constants).
- Amounts are validated as integers above zero. Currency, ids and state transitions are validated.
- The compose stack binds every port to 127.0.0.1.
- The OpenTelemetry agent is downloaded from Maven Central and its SHA-256 is checked (`scripts/fetch-otel-agent.sh`).
- The container image runs as a non-root user (uid 1000).
- No card data and no real personal data. Test accounts hold names such as `bank` and `acct-1`.

## Added in the reliability work

- An append-only caller audit log (`caller_audit`). A trigger rejects UPDATE and DELETE. Each row holds the client name, route pattern, idempotency key, outcome and trace id. It never holds the API key.
- A dead-letter topic `ledger.events.DLT` for malformed events.
- An admin redrive route that needs `X-Admin-Key`. With no admin key set, it answers 403.

## Known gaps

- A superuser or TRUNCATE can still remove audit rows. The trigger is not a full control.
- A failed audit write does not fail the request (ADR 0002). A counter shows the gap.
- Static API keys remain (see `docs/engagement/RISK-DECISION.md`).
- No rate limit on writes.

## What does not exist

- No OAuth2, no JWT, no per-client permissions, no key rotation. The static key `dev-key-local-only` in `application.yml` is a local development value.
  It is public in this repo. Never use it anywhere real. Real deployments must set their own keys from a secret store.
- No TLS. Traffic on the local stack is plain HTTP on the loopback interface.
- No rate limiting. A client can send many requests.
- Actuator endpoints `health` and `prometheus` are open. A real deployment must put them on a private port.
- Database and Redpanda use fixed local passwords or no authentication (`ledger/ledger` for Postgres). Local only.
- The caller audit log is append-only by trigger only (see Known gaps above).
- No encryption of data at rest or of Kafka messages.
- Dependency, image and secret scans exist as a workflow (`security.yml`) and ran locally. The workflow has not run on GitHub. Dependabot is not set up. CodeQL is configured in CI and has not run.
- No penetration test. A short STRIDE review of the money path is in `docs/engagement/THREAT-MODEL.md`.
- Payloads on Kafka carry account ids and amounts in clear text.

## PCI-DSS

This repo is out of PCI-DSS scope because it never sees card numbers. It makes no compliance claim.

## Report a problem

Open an issue on the repository or write to the owner. Do not put real keys or data in an issue.
