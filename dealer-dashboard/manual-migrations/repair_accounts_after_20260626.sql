-- MANUAL REPAIR ONLY.
-- Run this only when PRAGMA table_info(accounts) confirms that an already-deployed
-- database lost the columns below after the original 20260626_custom_plans.sql.
-- Do not add this file to the normal fresh-install migration loop.

ALTER TABLE accounts ADD COLUMN enrolled_by TEXT REFERENCES dealers(id);
ALTER TABLE accounts ADD COLUMN branch_id TEXT REFERENCES branches(id);
ALTER TABLE accounts ADD COLUMN agency_id TEXT REFERENCES agencies(id);
ALTER TABLE accounts ADD COLUMN ghana_card_verified INTEGER NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN ghana_card_status TEXT;
ALTER TABLE accounts ADD COLUMN ghana_card_verified_at INTEGER;
ALTER TABLE accounts ADD COLUMN didit_session_id TEXT;

UPDATE accounts
SET enrolled_by = COALESCE(enrolled_by, dealer_id),
    branch_id = COALESCE(branch_id, (SELECT branch_id FROM dealers WHERE id = accounts.dealer_id)),
    agency_id = COALESCE(agency_id, (SELECT agency_id FROM dealers WHERE id = accounts.dealer_id));

CREATE INDEX IF NOT EXISTS idx_accounts_enrolled_by ON accounts(enrolled_by);
CREATE INDEX IF NOT EXISTS idx_accounts_branch ON accounts(branch_id);
CREATE INDEX IF NOT EXISTS idx_accounts_agency ON accounts(agency_id);
