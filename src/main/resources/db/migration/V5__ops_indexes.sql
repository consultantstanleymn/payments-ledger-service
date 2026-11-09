-- Expand only: indexes that back the operations console keyset pages.
CREATE INDEX IF NOT EXISTS accounts_created_id ON accounts (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS payments_status_created_id ON payments (status, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS payments_created_id ON payments (created_at DESC, id DESC);
