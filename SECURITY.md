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

## What does not exist

- No OAuth2, no JWT, no per-client permissions, no key rotation. The static key `dev-key-local-only` in `application.yml` is a local development value.
  It is public in this repo. Never use it anywhere real. Real deployments must set their own keys from a secret store.
- No TLS. Traffic on the local stack is plain HTTP on the loopback interface.
- No rate limiting. A client can send many requests.
- Actuator endpoints `health` and `prometheus` are open. A real deployment must put them on a private port.
- Database and Redpanda use fixed local passwords or no authentication (`ledger/ledger` for Postgres). Local only.
- No audit log of who called what, beyond the journal and the idempotency table.
- No encryption of data at rest or of Kafka messages.
- No dependency scanning is configured, and Dependabot is not set up. CodeQL is configured in CI and has not run.
- No penetration test, no threat model beyond this list.
- Payloads on Kafka carry account ids and amounts in clear text.

## PCI-DSS

This repo is out of PCI-DSS scope because it never sees card numbers. It makes no compliance claim.

## Report a problem

Open an issue on the repository or write to the owner. Do not put real keys or data in an issue.
