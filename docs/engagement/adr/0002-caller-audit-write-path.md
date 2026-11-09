# ADR 0002: Caller audit write path

Status: accepted

## Context

Finance needs a record of who called the API, which route, and the outcome. The record must not change after it is written. The money path must stay fast and must not break when the audit write fails.

## Options

1. Write the audit row in the same transaction as the business change. A failed audit write rolls back the payment.
2. Write the audit row in a servlet filter after the response status is known. A failed write is logged and counted.
3. Publish audit events to Kafka and store them elsewhere.

## Decision

Use option 2. A servlet filter writes one row per call with `JdbcClient` after the response status is known. A failed write increments `ledger.caller_audit.write_failures` and writes a warning. The request result does not change.

The filter wraps the API key filter. This records calls that the key filter rejects, with client id `anonymous`. The row stores the client name that the key maps to. It never stores the key.

A database trigger rejects UPDATE and DELETE on `caller_audit`.

## Consequences

- A payment can succeed with no audit row if the audit write fails. The counter and a runbook entry make this visible. This is the trade-off.
- Option 1 gives a complete record but makes audit storage a single point of failure for payments.
- Rows are written outside the business transaction, so a rolled-back business change can still have an audit row. This is correct: the call happened.
- The trigger does not stop TRUNCATE or a superuser. A stronger control needs a separate store or database roles. This is a known gap in `SECURITY.md`.
