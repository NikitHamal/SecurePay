-- Customer recovery credentials for re-enrollment after an authorized reset.
-- Apply after 20260710_production_hardening.sql.
ALTER TABLE accounts ADD COLUMN customer_account_number TEXT;
ALTER TABLE accounts ADD COLUMN customer_pin_hash TEXT;
ALTER TABLE accounts ADD COLUMN customer_pin_updated_at INTEGER;

CREATE INDEX IF NOT EXISTS idx_accounts_customer_account_number
  ON accounts(customer_account_number);
