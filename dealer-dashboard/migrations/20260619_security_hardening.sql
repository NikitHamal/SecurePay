-- Apply once to existing D1 databases before generating new production QRs.
-- Adds dealer-scoped EFRP configuration and per-device API HMAC credentials.

ALTER TABLE dealers ADD COLUMN frp_account_ids TEXT NOT NULL DEFAULT '[]';
ALTER TABLE dealers ADD COLUMN security_policy_updated_at INTEGER;

ALTER TABLE accounts ADD COLUMN device_hmac_secret TEXT;
ALTER TABLE accounts ADD COLUMN device_hmac_secret_created_at INTEGER;

CREATE INDEX IF NOT EXISTS idx_accounts_device_secret ON accounts(device_hmac_secret_created_at);
