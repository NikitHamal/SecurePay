-- ============================================================
-- SecurePay Multi-Tenant Migration
-- Run this against your D1 database:
--   wrangler d1 execute securepay-db --file=MIGRATION_0002_MULTI_TENANT.sql
-- ============================================================

-- 1. Add role system to dealers
-- D1 does not support ALTER TABLE ADD COLUMN IF NOT EXISTS, so we use a workaround.
-- If the column already exists, D1 will error on that line. Run individually if needed.

-- Check if columns exist first (D1 compatible)
-- Note: If you get "duplicate column" errors, skip those ALTERs.

ALTER TABLE dealers ADD COLUMN role TEXT NOT NULL DEFAULT 'SUPER_ADMIN';
ALTER TABLE dealers ADD COLUMN agency_id TEXT;
ALTER TABLE dealers ADD COLUMN branch_id TEXT;
ALTER TABLE dealers ADD COLUMN is_approved INTEGER NOT NULL DEFAULT 1;
ALTER TABLE dealers ADD COLUMN approved_by TEXT;
ALTER TABLE dealers ADD COLUMN approved_at INTEGER;

-- 2. Organizational hierarchy tables
CREATE TABLE IF NOT EXISTS agencies (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    owner_id TEXT NOT NULL,
    phone TEXT,
    region TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL DEFAULT (unixepoch()),
    FOREIGN KEY (owner_id) REFERENCES dealers(id)
);

CREATE TABLE IF NOT EXISTS branches (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    agency_id TEXT NOT NULL,
    admin_id TEXT,
    address TEXT,
    phone TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL DEFAULT (unixepoch()),
    FOREIGN KEY (agency_id) REFERENCES agencies(id),
    FOREIGN KEY (admin_id) REFERENCES dealers(id)
);

-- 3. Agent self-registration
CREATE TABLE IF NOT EXISTS agent_requests (
    id TEXT PRIMARY KEY,
    full_name TEXT NOT NULL,
    email TEXT UNIQUE NOT NULL,
    phone TEXT NOT NULL,
    password TEXT NOT NULL,
    requested_branch_id TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    reviewed_by TEXT,
    reviewed_at INTEGER,
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
);

-- 4. Data isolation on accounts
ALTER TABLE accounts ADD COLUMN enrolled_by TEXT;
ALTER TABLE accounts ADD COLUMN branch_id TEXT;
ALTER TABLE accounts ADD COLUMN agency_id TEXT;

-- 5. Notifications
CREATE TABLE IF NOT EXISTS notifications (
    id TEXT PRIMARY KEY,
    recipient_id TEXT NOT NULL,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    is_read INTEGER NOT NULL DEFAULT 0,
    related_entity_type TEXT,
    related_entity_id TEXT,
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
);

-- 6. Ghana Card verification status
ALTER TABLE accounts ADD COLUMN ghana_card_verified INTEGER NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN ghana_card_status TEXT;
ALTER TABLE accounts ADD COLUMN ghana_card_verified_at INTEGER;
ALTER TABLE accounts ADD COLUMN didit_session_id TEXT;

-- 7. Indexes
CREATE INDEX IF NOT EXISTS idx_dealers_role ON dealers(role);
CREATE INDEX IF NOT EXISTS idx_dealers_agency ON dealers(agency_id);
CREATE INDEX IF NOT EXISTS idx_dealers_branch ON dealers(branch_id);
CREATE INDEX IF NOT EXISTS idx_accounts_enrolled_by ON accounts(enrolled_by);
CREATE INDEX IF NOT EXISTS idx_accounts_branch ON accounts(branch_id);
CREATE INDEX IF NOT EXISTS idx_accounts_agency ON accounts(agency_id);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_id, is_read);
CREATE INDEX IF NOT EXISTS idx_agent_requests_status ON agent_requests(status);
CREATE INDEX IF NOT EXISTS idx_agencies_owner ON agencies(owner_id);
CREATE INDEX IF NOT EXISTS idx_branches_agency ON branches(agency_id);

-- 8. Seed: make existing dealer the Super Admin
UPDATE dealers SET role = 'SUPER_ADMIN' WHERE email = 'dealer@securepay.io';

-- 9. Seed: default agency for client
INSERT OR IGNORE INTO agencies (id, name, owner_id, region)
VALUES (
    'AGY-001',
    'Touch Base Ghana',
    (SELECT id FROM dealers WHERE email = 'dealer@securepay.io'),
    'Greater Accra'
);

-- 10. Seed: default branch
INSERT OR IGNORE INTO branches (id, name, agency_id, address)
VALUES ('BR-001', 'Main Office - Accra', 'AGY-001', 'Accra, Ghana');

-- 11. Link existing dealer to agency/branch
UPDATE dealers
SET agency_id = 'AGY-001', branch_id = 'BR-001'
WHERE email = 'dealer@securepay.io';

-- 12. Update existing accounts
UPDATE accounts
SET branch_id = 'BR-001',
    agency_id = 'AGY-001',
    enrolled_by = (SELECT id FROM dealers WHERE email = 'dealer@securepay.io')
WHERE branch_id IS NULL;
