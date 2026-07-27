-- M-KOPA style application data captured during enrollment:
-- references (next of kin + referee), guarantor/signer, consent + signature.
ALTER TABLE accounts ADD COLUMN id_type TEXT;
ALTER TABLE accounts ADD COLUMN next_of_kin_name TEXT;
ALTER TABLE accounts ADD COLUMN next_of_kin_phone TEXT;
ALTER TABLE accounts ADD COLUMN next_of_kin_relation TEXT;
ALTER TABLE accounts ADD COLUMN referee_name TEXT;
ALTER TABLE accounts ADD COLUMN referee_phone TEXT;
ALTER TABLE accounts ADD COLUMN guarantor_name TEXT;
ALTER TABLE accounts ADD COLUMN guarantor_phone TEXT;
ALTER TABLE accounts ADD COLUMN guarantor_id_number TEXT;
ALTER TABLE accounts ADD COLUMN guarantor_relation TEXT;
ALTER TABLE accounts ADD COLUMN consent_terms INTEGER NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN consent_data INTEGER NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN consent_at INTEGER;
ALTER TABLE accounts ADD COLUMN customer_signature_path TEXT;
