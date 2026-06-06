-- SecurePay Database Schema
-- Apply with: turso db shell securepay < schema.sql

CREATE TABLE IF NOT EXISTS dealers (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  email       TEXT UNIQUE NOT NULL,
  phone       TEXT,
  password    TEXT NOT NULL,
  created_at  INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE IF NOT EXISTS plans (
  id              TEXT PRIMARY KEY,
  name            TEXT NOT NULL,
  term_days       INTEGER NOT NULL,
  total_amount    INTEGER NOT NULL,
  daily_rate      INTEGER NOT NULL,
  min_down_payment INTEGER NOT NULL,
  created_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE IF NOT EXISTS devices (
  id              TEXT PRIMARY KEY,
  imei            TEXT UNIQUE NOT NULL,
  model           TEXT NOT NULL,
  dealer_id       TEXT NOT NULL REFERENCES dealers(id),
  status          TEXT NOT NULL DEFAULT 'in_stock',
  created_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE IF NOT EXISTS accounts (
  id                      TEXT PRIMARY KEY,
  customer_name           TEXT NOT NULL,
  national_id             TEXT NOT NULL,
  phone_number            TEXT NOT NULL,
  device_id               TEXT UNIQUE NOT NULL REFERENCES devices(id),
  dealer_id               TEXT NOT NULL REFERENCES dealers(id),
  plan_id                 TEXT NOT NULL REFERENCES plans(id),
  total_loan_amount       INTEGER NOT NULL,
  amount_paid             INTEGER NOT NULL DEFAULT 0,
  daily_rate              INTEGER NOT NULL,
  next_payment_due        INTEGER NOT NULL,
  status                  TEXT NOT NULL DEFAULT 'ACTIVE',
  locked_by_dealer        INTEGER NOT NULL DEFAULT 0,
  down_payment            INTEGER NOT NULL,
  term_days               INTEGER NOT NULL,
  currency_code           TEXT NOT NULL DEFAULT 'KES',
  created_at              INTEGER NOT NULL DEFAULT (unixepoch()),
  updated_at              INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE IF NOT EXISTS payments (
  id              TEXT PRIMARY KEY,
  account_id      TEXT NOT NULL REFERENCES accounts(id),
  amount          INTEGER NOT NULL,
  method          TEXT NOT NULL,
  reference       TEXT,
  recorded_by    TEXT NOT NULL,
  created_at     INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE IF NOT EXISTS lock_events (
  id              TEXT PRIMARY KEY,
  account_id      TEXT NOT NULL REFERENCES accounts(id),
  event_type      TEXT NOT NULL,
  triggered_by    TEXT NOT NULL,
  created_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE IF NOT EXISTS sessions (
  id              TEXT PRIMARY KEY,
  dealer_id       TEXT NOT NULL REFERENCES dealers(id),
  token           TEXT UNIQUE NOT NULL,
  expires_at      INTEGER NOT NULL,
  created_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_accounts_dealer ON accounts(dealer_id);
CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts(status);
CREATE INDEX IF NOT EXISTS idx_accounts_phone  ON accounts(phone_number);
CREATE INDEX IF NOT EXISTS idx_accounts_due    ON accounts(next_payment_due);
CREATE INDEX IF NOT EXISTS idx_payments_account ON payments(account_id);
CREATE INDEX IF NOT EXISTS idx_payments_created ON payments(created_at);
CREATE INDEX IF NOT EXISTS idx_devices_imei    ON devices(imei);
CREATE INDEX IF NOT EXISTS idx_devices_dealer  ON devices(dealer_id);
CREATE INDEX IF NOT EXISTS idx_lock_events_account ON lock_events(account_id);

-- Seed plans
INSERT OR IGNORE INTO plans (id, name, term_days, total_amount, daily_rate, min_down_payment) VALUES
  ('plan-lite-90',    'Lite 90',      90,  1200000, 13333,  300000),
  ('plan-standard-180','Standard 180',180, 2400000, 13333,  600000),
  ('plan-premium-365','Premium 365', 365, 4500000, 12329, 1000000);

-- Seed demo dealer (password: "dealer123" — bcrypt hash)
INSERT OR IGNORE INTO dealers (id, name, email, phone, password) VALUES
  ('dealer-demo-001', 'Demo Dealer', 'dealer@securepay.io', '+254700000001', '$2b$10$PXxK0hfGIKWvEk3C62eJqOHMRHeM1TODgc3QoevbSYS8jTlbulrb.');