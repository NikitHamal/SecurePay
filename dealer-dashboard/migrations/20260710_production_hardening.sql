-- Production hardening: Didit audit trail, query indexes, and decision storage.
ALTER TABLE accounts ADD COLUMN didit_decision TEXT;

CREATE TABLE IF NOT EXISTS didit_webhook_events (
  event_id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL,
  account_id TEXT NOT NULL REFERENCES accounts(id),
  webhook_type TEXT,
  status TEXT,
  received_at INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_didit_events_account ON didit_webhook_events(account_id, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_didit_events_session ON didit_webhook_events(session_id);
CREATE INDEX IF NOT EXISTS idx_accounts_scope_agency ON accounts(agency_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_accounts_scope_branch ON accounts(branch_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_accounts_scope_agent ON accounts(enrolled_by, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payments_recorder ON payments(recorded_by, created_at DESC);

-- Disable the known demo credential if it exists in an older deployment.
UPDATE dealers
SET password = '!disabled-default-account!', is_approved = 0
WHERE id = 'dealer-demo-001' AND lower(email) = 'dealer@securepay.io';
