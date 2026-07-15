-- Enrich device_logs with device/customer identity.
-- Apply after 20260713_sync_reports.sql.
ALTER TABLE device_logs ADD COLUMN account_id TEXT;
ALTER TABLE device_logs ADD COLUMN imei TEXT;
ALTER TABLE device_logs ADD COLUMN device_model TEXT;

CREATE INDEX IF NOT EXISTS idx_device_logs_account ON device_logs(account_id, created_at);
