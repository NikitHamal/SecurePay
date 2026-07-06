-- Stolen-device tracking support.
-- Apply after 20260627/20260702 migrations.
-- NOTE: Cloudflare D1/SQLite does not support ALTER TABLE ADD COLUMN IF NOT EXISTS.
-- If you already manually added accounts.is_stolen, skip only that ALTER line and
-- still run the CREATE TABLE / CREATE INDEX statements.

ALTER TABLE accounts ADD COLUMN is_stolen INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS location_logs (
  id              TEXT PRIMARY KEY,
  account_id      TEXT NOT NULL REFERENCES accounts(id),
  latitude        REAL NOT NULL,
  longitude       REAL NOT NULL,
  accuracy        REAL,
  battery_level   INTEGER,
  timestamp       INTEGER NOT NULL,
  created_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_loc_account ON location_logs(account_id);
CREATE INDEX IF NOT EXISTS idx_loc_time ON location_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_loc_account_time ON location_logs(account_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_accounts_is_stolen ON accounts(is_stolen);
