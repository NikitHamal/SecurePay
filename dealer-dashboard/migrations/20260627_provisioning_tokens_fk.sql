-- Rebuild provisioning_tokens FK reference that was left dangling
-- by 20260626_custom_plans.sql. The accounts table was renamed
-- accounts_old → accounts during the custom-plans migration, and
-- SQLite automatically rewrote the FK to follow the rename.
-- After accounts_old was dropped, the FK now points at nothing and
-- every INSERT fails with FOREIGN KEY constraint failed.

PRAGMA foreign_keys = OFF;

CREATE TABLE provisioning_tokens_new (
  id              TEXT PRIMARY KEY,
  account_id      TEXT NOT NULL REFERENCES accounts(id),
  device_id       TEXT NOT NULL REFERENCES devices(id),
  dealer_id       TEXT NOT NULL REFERENCES dealers(id),
  activation_code TEXT NOT NULL UNIQUE,
  status          TEXT NOT NULL DEFAULT 'pending',
  wifi_ssid       TEXT,
  wifi_password   TEXT,
  created_at      INTEGER NOT NULL DEFAULT (unixepoch()),
  expires_at      INTEGER NOT NULL,
  provisioned_at  INTEGER,
  activated_at    INTEGER
);

INSERT INTO provisioning_tokens_new (
  id, account_id, device_id, dealer_id, activation_code, status,
  wifi_ssid, wifi_password, created_at, expires_at,
  provisioned_at, activated_at
)
SELECT
  id, account_id, device_id, dealer_id, activation_code, status,
  wifi_ssid, wifi_password, created_at, expires_at,
  provisioned_at, activated_at
FROM provisioning_tokens;

DROP TABLE provisioning_tokens;

ALTER TABLE provisioning_tokens_new RENAME TO provisioning_tokens;

CREATE INDEX IF NOT EXISTS idx_prov_code ON provisioning_tokens(activation_code);
CREATE INDEX IF NOT EXISTS idx_prov_device ON provisioning_tokens(device_id);
CREATE INDEX IF NOT EXISTS idx_prov_status ON provisioning_tokens(status);

PRAGMA foreign_keys = ON;
