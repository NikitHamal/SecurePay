-- Admin-controlled pricing + IMEI assignment + down-payment approval workflow
-- Implements: 1) Admin owns device catalog/pricing, 2) Agent sees only assigned IMEIs,
--             3) Down payment must be confirmed by admin before provisioning.

-- 1. Product catalog: admin-defined phone models and payment plans.
CREATE TABLE IF NOT EXISTS product_models (
  id            TEXT PRIMARY KEY,
  name          TEXT NOT NULL UNIQUE,
  model         TEXT NOT NULL,
  description   TEXT,
  total_amount  INTEGER NOT NULL,
  down_payment  INTEGER NOT NULL,
  daily_rate    INTEGER NOT NULL,
  term_days     INTEGER NOT NULL,
  created_by    TEXT REFERENCES dealers(id),
  is_active     INTEGER NOT NULL DEFAULT 1,
  created_at    INTEGER NOT NULL DEFAULT (unixepoch()),
  updated_at    INTEGER NOT NULL DEFAULT (unixepoch())
);
CREATE INDEX IF NOT EXISTS idx_product_models_active ON product_models(is_active);
CREATE INDEX IF NOT EXISTS idx_product_models_model  ON product_models(model);

-- 2. Extend devices with assignment + locked pricing (in pesewas + days).
ALTER TABLE devices ADD COLUMN product_model_id TEXT REFERENCES product_models(id);
ALTER TABLE devices ADD COLUMN assigned_to TEXT REFERENCES dealers(id);
ALTER TABLE devices ADD COLUMN assigned_at INTEGER;
ALTER TABLE devices ADD COLUMN assigned_by TEXT REFERENCES dealers(id);
ALTER TABLE devices ADD COLUMN total_amount INTEGER;
ALTER TABLE devices ADD COLUMN down_payment INTEGER;
ALTER TABLE devices ADD COLUMN daily_rate INTEGER;
ALTER TABLE devices ADD COLUMN term_days INTEGER;
ALTER TABLE devices ADD COLUMN down_payment_status TEXT DEFAULT 'unpaid';
CREATE INDEX IF NOT EXISTS idx_devices_assigned_to ON devices(assigned_to);
CREATE INDEX IF NOT EXISTS idx_devices_product_model ON devices(product_model_id);
CREATE INDEX IF NOT EXISTS idx_devices_pricing ON devices(product_model_id, status);

-- 3. Down-payment submissions: agent submits cash received, admin confirms/rejects.
CREATE TABLE IF NOT EXISTS down_payment_submissions (
  id            TEXT PRIMARY KEY,
  account_id    TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  device_id     TEXT NOT NULL REFERENCES devices(id),
  agent_id      TEXT NOT NULL REFERENCES dealers(id),
  amount        INTEGER NOT NULL,
  status        TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','confirmed','rejected','cancelled')),
  method        TEXT NOT NULL DEFAULT 'cash',
  reference     TEXT,
  submitted_at  INTEGER NOT NULL DEFAULT (unixepoch()),
  confirmed_by  TEXT REFERENCES dealers(id),
  confirmed_at  INTEGER,
  note          TEXT,
  created_at    INTEGER NOT NULL DEFAULT (unixepoch()),
  updated_at    INTEGER NOT NULL DEFAULT (unixepoch())
);
CREATE INDEX IF NOT EXISTS idx_down_pay_agent   ON down_payment_submissions(agent_id, status);
CREATE INDEX IF NOT EXISTS idx_down_pay_status  ON down_payment_submissions(status, submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_down_pay_account ON down_payment_submissions(account_id);

-- 4. Track who approved device provisioning eligibility (audit).
ALTER TABLE accounts ADD COLUMN down_payment_status TEXT DEFAULT 'unpaid';
ALTER TABLE accounts ADD COLUMN down_payment_confirmed_by TEXT REFERENCES dealers(id);
ALTER TABLE accounts ADD COLUMN down_payment_confirmed_at INTEGER;
CREATE INDEX IF NOT EXISTS idx_accounts_down_status ON accounts(down_payment_status);
