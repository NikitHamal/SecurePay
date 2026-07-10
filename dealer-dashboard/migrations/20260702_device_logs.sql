-- Create device_logs table for remote diagnostics from customer DPC app.
-- POST /api/device/logs is authenticated with the deployment HMAC so that
-- unactivated devices can send provisioning-stage diagnostics without exposing a public write endpoint.

CREATE TABLE IF NOT EXISTS device_logs (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  tag         TEXT NOT NULL,
  message     TEXT NOT NULL,
  level       TEXT NOT NULL DEFAULT 'INFO',
  created_at  INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_device_logs_created_at ON device_logs(created_at);
