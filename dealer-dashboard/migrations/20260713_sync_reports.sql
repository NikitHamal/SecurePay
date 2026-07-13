-- Sync reports for 2-way device communication.
-- Apply after 20260713_customer_recovery_login.sql.
ALTER TABLE accounts ADD COLUMN last_sync_at INTEGER;

CREATE TABLE IF NOT EXISTS sync_reports (
  id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL,
  imei TEXT NOT NULL,
  app_version TEXT,
  battery_level INTEGER,
  lat REAL,
  lng REAL,
  reported_at INTEGER NOT NULL,
  FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE INDEX IF NOT EXISTS idx_sync_reports_account_id
  ON sync_reports(account_id, reported_at);
