-- Accounts keep a cached balance. The journal is the source of truth.
CREATE TABLE accounts (
    id           uuid PRIMARY KEY,
    name         text    NOT NULL,
    currency     char(3) NOT NULL,
    floor_minor  bigint  NOT NULL DEFAULT 0 CHECK (floor_minor <= 0),
    posted_minor bigint  NOT NULL DEFAULT 0,
    held_minor   bigint  NOT NULL DEFAULT 0 CHECK (held_minor >= 0),
    created_at   timestamptz NOT NULL DEFAULT now(),
    -- the cached balance can never break the floor
    CONSTRAINT accounts_floor CHECK (posted_minor - held_minor >= floor_minor)
);

CREATE TABLE journal_transactions (
    id         uuid PRIMARY KEY,
    kind       text NOT NULL,
    reference  text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE journal_entries (
    id           bigserial PRIMARY KEY,
    txn_id       uuid   NOT NULL REFERENCES journal_transactions (id),
    account_id   uuid   NOT NULL REFERENCES accounts (id),
    amount_minor bigint NOT NULL CHECK (amount_minor <> 0)
);
CREATE INDEX journal_entries_account ON journal_entries (account_id);
CREATE INDEX journal_entries_txn ON journal_entries (txn_id);

-- Entries of one transaction must sum to zero. Checked at commit time.
CREATE FUNCTION check_txn_balanced() RETURNS trigger AS $$
DECLARE total bigint;
BEGIN
    SELECT COALESCE(SUM(amount_minor), 0) INTO total FROM journal_entries WHERE txn_id = NEW.txn_id;
    IF total <> 0 THEN
        RAISE EXCEPTION 'journal transaction % does not sum to zero (sum=%)', NEW.txn_id, total;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER journal_entries_balanced
    AFTER INSERT ON journal_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_txn_balanced();

CREATE TABLE payments (
    id                uuid PRIMARY KEY,
    payer_account_id  uuid   NOT NULL REFERENCES accounts (id),
    payee_account_id  uuid   NOT NULL REFERENCES accounts (id),
    currency          char(3) NOT NULL,
    authorized_minor  bigint NOT NULL CHECK (authorized_minor > 0),
    captured_minor    bigint NOT NULL DEFAULT 0,
    refunded_minor    bigint NOT NULL DEFAULT 0,
    status            text   NOT NULL CHECK (status IN ('AUTHORIZED', 'CAPTURED', 'VOIDED')),
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_keys (
    client_id    text NOT NULL,
    idem_key     text NOT NULL,
    request_hash text NOT NULL,
    status       int,
    response     text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (client_id, idem_key)
);

CREATE TABLE outbox_events (
    seq          bigserial PRIMARY KEY,
    event_id     uuid NOT NULL UNIQUE,
    aggregate_id uuid NOT NULL,
    type         text NOT NULL,
    payload      text NOT NULL,
    traceparent  text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    sent_at      timestamptz
);
CREATE INDEX outbox_unsent ON outbox_events (seq) WHERE sent_at IS NULL;

CREATE TABLE balance_projection (
    account_id   uuid PRIMARY KEY,
    posted_minor bigint NOT NULL DEFAULT 0,
    held_minor   bigint NOT NULL DEFAULT 0,
    event_count  bigint NOT NULL DEFAULT 0
);

CREATE TABLE processed_events (
    event_id     uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE recon_reports (
    id         uuid PRIMARY KEY,
    matched    int NOT NULL,
    missing    int NOT NULL,
    mismatched int NOT NULL,
    unexpected int NOT NULL,
    details    text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
