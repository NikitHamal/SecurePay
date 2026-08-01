-- Accountability & audit round (Aug 2026)
-- 1. Agent activity log (who did what, when, from which branch/location)
-- 2. GPS context captured when a phone is registered into inventory
-- 3. GPS context captured when a customer enrollment is submitted
-- 4. De-duplicated per-day payment reminders sent to customer devices

-- 1. Agent activity log -----------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_activity (
  id            TEXT PRIMARY KEY,
  actor_id      TEXT NOT NULL REFERENCES dealers(id),
  actor_name    TEXT NOT NULL,
  actor_role    TEXT NOT NULL,
  action        TEXT NOT NULL,
  details       TEXT NOT NULL DEFAULT '',
  customer_name TEXT,
  account_id    TEXT,
  imei          TEXT,
  branch_name   TEXT,
  agency_id     TEXT,
  branch_id     TEXT,
  latitude      REAL,
  longitude     REAL,
  created_at    INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_agent_activity_created ON agent_activity(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_activity_actor   ON agent_activity(actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_activity_scope   ON agent_activity(agency_id, branch_id, created_at DESC);

-- 2. Device registration GPS ------------------------------------------------
ALTER TABLE devices ADD COLUMN reg_lat      REAL;
ALTER TABLE devices ADD COLUMN reg_lng      REAL;
ALTER TABLE devices ADD COLUMN reg_accuracy REAL;

-- 3. Enrollment GPS ---------------------------------------------------------
ALTER TABLE accounts ADD COLUMN enrollment_lat      REAL;
ALTER TABLE accounts ADD COLUMN enrollment_lng      REAL;
ALTER TABLE accounts ADD COLUMN enrollment_accuracy REAL;

-- 4. Payment reminder de-dup (one push per account per day) ------------------
CREATE TABLE IF NOT EXISTS payment_reminders (
  account_id  TEXT NOT NULL REFERENCES accounts(id),
  reminder_day TEXT NOT NULL,
  created_at  INTEGER NOT NULL DEFAULT (unixepoch()),
  PRIMARY KEY (account_id, reminder_day)
);

-- Duplicate-ID lookups run on a normalized expression; keep the base column indexed.
CREATE INDEX IF NOT EXISTS idx_accounts_national_id ON accounts(national_id);
