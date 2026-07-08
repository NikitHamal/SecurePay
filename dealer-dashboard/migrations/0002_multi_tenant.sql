-- Multi-tenant hierarchy migration
-- Adds support for Super Admin → Agency → Branch → Agent hierarchy
-- and Ghana Card verification via Didit

-- 1. Add role system to dealers table
ALTER TABLE dealers ADD COLUMN role TEXT DEFAULT 'SUPER_ADMIN';
ALTER TABLE dealers ADD COLUMN agency_id TEXT REFERENCES agencies(id);
ALTER TABLE dealers ADD COLUMN branch_id TEXT REFERENCES branches(id);
ALTER TABLE dealers ADD COLUMN is_approved INTEGER DEFAULT 1;
ALTER TABLE dealers ADD COLUMN approved_by TEXT;
ALTER TABLE dealers ADD COLUMN approved_at INTEGER;

-- 2. Create agencies table (DSL Agencies - Regional Leaders)
CREATE TABLE IF NOT EXISTS agencies (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  owner_id TEXT NOT NULL REFERENCES dealers(id),
  phone TEXT,
  region TEXT,
  is_active INTEGER DEFAULT 1,
  created_at INTEGER DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_agencies_owner ON agencies(owner_id);

-- 3. Create branches table (Physical locations under agencies)
CREATE TABLE IF NOT EXISTS branches (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  agency_id TEXT NOT NULL REFERENCES agencies(id),
  admin_id TEXT REFERENCES dealers(id),
  address TEXT,
  phone TEXT,
  is_active INTEGER DEFAULT 1,
  created_at INTEGER DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_branches_agency ON branches(agency_id);
CREATE INDEX IF NOT EXISTS idx_branches_admin ON branches(admin_id);

-- 4. Create agent_requests table (Self-registration for agents)
CREATE TABLE IF NOT EXISTS agent_requests (
  id TEXT PRIMARY KEY,
  full_name TEXT NOT NULL,
  email TEXT UNIQUE NOT NULL,
  phone TEXT NOT NULL,
  password TEXT NOT NULL,
  requested_branch_id TEXT REFERENCES branches(id),
  status TEXT DEFAULT 'PENDING',
  reviewed_by TEXT REFERENCES dealers(id),
  reviewed_at INTEGER,
  created_at INTEGER DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_agent_requests_status ON agent_requests(status);
CREATE INDEX IF NOT EXISTS idx_agent_requests_email ON agent_requests(email);

-- 5. Create notifications table
CREATE TABLE IF NOT EXISTS notifications (
  id TEXT PRIMARY KEY,
  recipient_id TEXT NOT NULL REFERENCES dealers(id),
  type TEXT NOT NULL,
  title TEXT NOT NULL,
  message TEXT NOT NULL,
  is_read INTEGER DEFAULT 0,
  related_entity_type TEXT,
  related_entity_id TEXT,
  created_at INTEGER DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_id);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read ON notifications(recipient_id, is_read);
CREATE INDEX IF NOT EXISTS idx_notifications_created ON notifications(created_at);

-- 6. Add data isolation columns to accounts
ALTER TABLE accounts ADD COLUMN enrolled_by TEXT REFERENCES dealers(id);
ALTER TABLE accounts ADD COLUMN branch_id TEXT REFERENCES branches(id);
ALTER TABLE accounts ADD COLUMN agency_id TEXT REFERENCES agencies(id);

CREATE INDEX IF NOT EXISTS idx_accounts_enrolled_by ON accounts(enrolled_by);
CREATE INDEX IF NOT EXISTS idx_accounts_branch ON accounts(branch_id);
CREATE INDEX IF NOT EXISTS idx_accounts_agency ON accounts(agency_id);

-- 7. Add Ghana Card verification columns to accounts
ALTER TABLE accounts ADD COLUMN ghana_card_verified INTEGER DEFAULT 0;
ALTER TABLE accounts ADD COLUMN ghana_card_status TEXT;
ALTER TABLE accounts ADD COLUMN ghana_card_verified_at INTEGER;
ALTER TABLE accounts ADD COLUMN didit_session_id TEXT;

-- Backfill existing accounts with current dealer's info
UPDATE accounts
SET enrolled_by = dealer_id,
    branch_id = (SELECT branch_id FROM dealers WHERE id = accounts.dealer_id),
    agency_id = (SELECT agency_id FROM dealers WHERE id = accounts.dealer_id)
WHERE enrolled_by IS NULL;

-- 9. Create rate_limits table for API rate limiting
CREATE TABLE IF NOT EXISTS rate_limits (
  key TEXT NOT NULL,
  created_at INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX IF NOT EXISTS idx_rate_limits_key ON rate_limits(key);
CREATE INDEX IF NOT EXISTS idx_rate_limits_created ON rate_limits(created_at);
