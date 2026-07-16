-- Apply to the production D1 database before deploying the v2 dashboard.

CREATE TABLE IF NOT EXISTS hmac_nonces (
  nonce       TEXT PRIMARY KEY,
  created_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_hmac_nonces_created_at ON hmac_nonces(created_at);

-- Wi-Fi credentials are now used only to construct the one-time QR payload and
-- are no longer persisted by the dashboard.
UPDATE provisioning_tokens SET wifi_password = NULL WHERE wifi_password IS NOT NULL;

-- Ghana production normalization. Values remain integer minor units (pesewas).
UPDATE accounts SET currency_code = 'GHS' WHERE currency_code = 'KES';
UPDATE payments SET method = 'mobile_money' WHERE lower(replace(method, '-', '')) IN ('mpesa', 'm_pesa', 'momo', 'mobilemoney');
UPDATE payments SET method = 'bank' WHERE lower(method) = 'bank_transfer';
