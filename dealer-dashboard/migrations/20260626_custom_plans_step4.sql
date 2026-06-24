DROP TABLE accounts_old;

CREATE INDEX IF NOT EXISTS idx_accounts_dealer ON accounts(dealer_id);
CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts(status);
CREATE INDEX IF NOT EXISTS idx_accounts_phone  ON accounts(phone_number);
CREATE INDEX IF NOT EXISTS idx_accounts_due    ON accounts(next_payment_due);
CREATE INDEX IF NOT EXISTS idx_accounts_device_secret ON accounts(device_hmac_secret_created_at);

PRAGMA foreign_keys = ON;
