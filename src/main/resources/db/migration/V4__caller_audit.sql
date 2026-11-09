-- Append-only record of every API call: who called, which route, outcome, trace.
CREATE TABLE caller_audit (
    id              bigserial PRIMARY KEY,
    at              timestamptz NOT NULL DEFAULT now(),
    client_id       text NOT NULL,
    method          text NOT NULL,
    route           text NOT NULL,
    idempotency_key text,
    status          int  NOT NULL,
    outcome         text NOT NULL,
    trace_id        text
);

CREATE FUNCTION caller_audit_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'caller_audit is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER caller_audit_no_change
    BEFORE UPDATE OR DELETE ON caller_audit
    FOR EACH ROW EXECUTE FUNCTION caller_audit_immutable();
