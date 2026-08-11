-- USSD account-number entry: remember which account a session resolved to.

ALTER TABLE ussd_sessions ADD COLUMN account_id TEXT;
