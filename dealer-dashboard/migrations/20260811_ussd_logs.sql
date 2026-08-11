-- Request/response log for the JEST USSD endpoint (/api/ussd)
-- Lets us diagnose gateway errors (e.g. 506 on the handset) after the fact.

CREATE TABLE IF NOT EXISTS ussd_logs (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id        TEXT,
  msisdn            TEXT,
  user_data         TEXT,
  network           TEXT,
  msg_type          INTEGER,
  response_msg      TEXT,
  response_msg_type INTEGER,
  error             TEXT,
  created_at        INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_ussd_logs_created ON ussd_logs(created_at);
