-- Apply once to existing D1 databases that were created before release lifecycle support.
-- For brand-new databases, migrations/0001_initial.sql already contains these columns.

ALTER TABLE accounts ADD COLUMN release_approved INTEGER NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN release_approved_at INTEGER;
ALTER TABLE accounts ADD COLUMN released_at INTEGER;

UPDATE accounts
   SET release_approved = 1,
       release_approved_at = COALESCE(release_approved_at, updated_at)
 WHERE amount_paid >= total_loan_amount
   AND release_approved = 0;
