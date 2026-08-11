-- USSD self-service sessions (JEST USSD aggregator -> /api/ussd)
-- Sessions are short-lived; state lets the stateless USSD channel
-- resume menus across requests within the TTL window.

CREATE TABLE IF NOT EXISTS ussd_sessions (
  session_id      TEXT PRIMARY KEY,
  msisdn          TEXT NOT NULL,
  network         TEXT,
  step            TEXT NOT NULL DEFAULT 'main',
  amount_pesewas  INTEGER,
  provider        TEXT,
  paystack_ref    TEXT,
  created_at      INTEGER NOT NULL DEFAULT (unixepoch()),
  updated_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_ussd_sessions_updated ON ussd_sessions(updated_at);
