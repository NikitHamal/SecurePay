-- Rebuild all dangling FK references that point at the dropped accounts_old
-- table. After 20260626_custom_plans.sql dropped accounts_old, every table
-- that referenced it (via SQLite's auto-rename behaviour) has a dangling FK
-- and any INSERT into it fails with FOREIGN KEY constraint failed.
--
-- Affected tables:
--   payments, lock_events  (and provisioning_tokens, fixed in 20260627)

PRAGMA foreign_keys = OFF;

CREATE TABLE payments_new (
  id              TEXT PRIMARY KEY,
  account_id      TEXT NOT NULL REFERENCES accounts(id),
  amount          INTEGER NOT NULL,
  method          TEXT NOT NULL,
  reference       TEXT,
  recorded_by    TEXT NOT NULL,
  created_at     INTEGER NOT NULL DEFAULT (unixepoch())
);

INSERT INTO payments_new (id, account_id, amount, method, reference, recorded_by, created_at)
SELECT id, account_id, amount, method, reference, recorded_by, created_at
FROM payments;

DROP TABLE payments;
ALTER TABLE payments_new RENAME TO payments;

CREATE TABLE lock_events_new (
  id              TEXT PRIMARY KEY,
  account_id      TEXT NOT NULL REFERENCES accounts(id),
  event_type      TEXT NOT NULL,
  triggered_by    TEXT NOT NULL,
  created_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

INSERT INTO lock_events_new (id, account_id, event_type, triggered_by, created_at)
SELECT id, account_id, event_type, triggered_by, created_at
FROM lock_events;

DROP TABLE lock_events;
ALTER TABLE lock_events_new RENAME TO lock_events;

CREATE INDEX IF NOT EXISTS idx_payments_account ON payments(account_id);
CREATE INDEX IF NOT EXISTS idx_payments_created ON payments(created_at);
CREATE INDEX IF NOT EXISTS idx_lock_events_account ON lock_events(account_id);

PRAGMA foreign_keys = ON;
