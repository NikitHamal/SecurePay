-- Paystack payment integration
-- Stores Paystack transactions and ties them to payments/accounts.

CREATE TABLE IF NOT EXISTS paystack_transactions (
  id                INTEGER PRIMARY KEY,  -- Paystack transaction id (uint64 where available)
  reference         TEXT UNIQUE NOT NULL,
  access_code       TEXT,
  authorization_code TEXT,
  account_id        TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  dealer_id         TEXT NOT NULL REFERENCES dealers(id),
  amount            INTEGER NOT NULL,             -- in pesewas (kobo subunit for GHS)
  currency          TEXT NOT NULL DEFAULT 'GHS',
  channel           TEXT,
  provider          TEXT,                        -- mtn | vodafone | airteltigo | card
  customer_email    TEXT,
  customer_phone    TEXT,
  status            TEXT NOT NULL DEFAULT 'pending', -- pending|send_otp|otp_sent|success|failed|abandoned|reversed
  gateway_response  TEXT,
  fees              INTEGER,
  paid_at           INTEGER,                     -- unix seconds
  metadata_json     TEXT,
  payment_id        TEXT REFERENCES payments(id) ON DELETE SET NULL,
  created_at        INTEGER NOT NULL DEFAULT (unixepoch()),
  updated_at        INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_paystack_account     ON paystack_transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_paystack_status      ON paystack_transactions(status);
CREATE INDEX IF NOT EXISTS idx_paystack_reference   ON paystack_transactions(reference);
CREATE INDEX IF NOT EXISTS idx_paystack_created     ON paystack_transactions(created_at);
