-- Make plan_id nullable to support custom (non-plan) account terms.
-- IMPORTANT: this rebuild must preserve every column added by earlier migrations,
-- including multi-tenant scope and Ghana Card / Didit state.

ALTER TABLE accounts RENAME TO accounts_old;

CREATE TABLE accounts (
  id                      TEXT PRIMARY KEY,
  customer_name           TEXT NOT NULL,
  national_id             TEXT NOT NULL,
  phone_number            TEXT NOT NULL,
  device_id               TEXT UNIQUE NOT NULL REFERENCES devices(id),
  dealer_id               TEXT NOT NULL REFERENCES dealers(id),
  plan_id                 TEXT REFERENCES plans(id),
  total_loan_amount       INTEGER NOT NULL,
  amount_paid             INTEGER NOT NULL DEFAULT 0,
  daily_rate              INTEGER NOT NULL,
  next_payment_due        INTEGER NOT NULL,
  status                  TEXT NOT NULL DEFAULT 'ACTIVE',
  locked_by_dealer        INTEGER NOT NULL DEFAULT 0,
  down_payment            INTEGER NOT NULL,
  term_days               INTEGER NOT NULL,
  currency_code           TEXT NOT NULL DEFAULT 'GHS',
  release_approved        INTEGER NOT NULL DEFAULT 0,
  release_approved_at     INTEGER,
  released_at             INTEGER,
  device_hmac_secret      TEXT,
  device_hmac_secret_created_at INTEGER,
  customer_photo_path     TEXT,
  national_id_front_path  TEXT,
  national_id_back_path   TEXT,
  fcm_token               TEXT,
  fcm_token_updated_at    INTEGER,
  enrolled_by             TEXT REFERENCES dealers(id),
  branch_id               TEXT REFERENCES branches(id),
  agency_id               TEXT REFERENCES agencies(id),
  ghana_card_verified     INTEGER NOT NULL DEFAULT 0,
  ghana_card_status       TEXT,
  ghana_card_verified_at  INTEGER,
  didit_session_id        TEXT,
  created_at              INTEGER NOT NULL DEFAULT (unixepoch()),
  updated_at              INTEGER NOT NULL DEFAULT (unixepoch())
);

INSERT INTO accounts (
  id, customer_name, national_id, phone_number, device_id, dealer_id, plan_id,
  total_loan_amount, amount_paid, daily_rate, next_payment_due, status,
  locked_by_dealer, down_payment, term_days, currency_code,
  release_approved, release_approved_at, released_at,
  device_hmac_secret, device_hmac_secret_created_at,
  customer_photo_path, national_id_front_path, national_id_back_path,
  fcm_token, fcm_token_updated_at,
  enrolled_by, branch_id, agency_id,
  ghana_card_verified, ghana_card_status, ghana_card_verified_at, didit_session_id,
  created_at, updated_at
)
SELECT
  id, customer_name, national_id, phone_number, device_id, dealer_id, plan_id,
  total_loan_amount, amount_paid, daily_rate, next_payment_due, status,
  locked_by_dealer, down_payment, term_days, currency_code,
  release_approved, release_approved_at, released_at,
  device_hmac_secret, device_hmac_secret_created_at,
  customer_photo_path, national_id_front_path, national_id_back_path,
  fcm_token, fcm_token_updated_at,
  enrolled_by, branch_id, agency_id,
  ghana_card_verified, ghana_card_status, ghana_card_verified_at, didit_session_id,
  created_at, updated_at
FROM accounts_old;

DROP TABLE accounts_old;

-- Recreate indexes lost with the old table.
CREATE INDEX IF NOT EXISTS idx_accounts_dealer ON accounts(dealer_id);
CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts(status);
CREATE INDEX IF NOT EXISTS idx_accounts_phone ON accounts(phone_number);
CREATE INDEX IF NOT EXISTS idx_accounts_due ON accounts(next_payment_due);
CREATE INDEX IF NOT EXISTS idx_accounts_device_secret ON accounts(device_hmac_secret_created_at);
CREATE INDEX IF NOT EXISTS idx_accounts_enrolled_by ON accounts(enrolled_by);
CREATE INDEX IF NOT EXISTS idx_accounts_branch ON accounts(branch_id);
CREATE INDEX IF NOT EXISTS idx_accounts_agency ON accounts(agency_id);
