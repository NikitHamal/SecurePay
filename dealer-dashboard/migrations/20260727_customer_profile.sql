-- M-KOPA style application profile captured during enrollment (personal
-- details + location), plus the exact loan agreement text the customer signed.
ALTER TABLE accounts ADD COLUMN surname TEXT;
ALTER TABLE accounts ADD COLUMN other_phone TEXT;
ALTER TABLE accounts ADD COLUMN date_of_birth TEXT;
ALTER TABLE accounts ADD COLUMN marital_status TEXT;
ALTER TABLE accounts ADD COLUMN employment_status TEXT;
ALTER TABLE accounts ADD COLUMN gender TEXT;
ALTER TABLE accounts ADD COLUMN is_customer_user INTEGER;
ALTER TABLE accounts ADD COLUMN region TEXT;
ALTER TABLE accounts ADD COLUMN district TEXT;
ALTER TABLE accounts ADD COLUMN physical_address TEXT;
ALTER TABLE accounts ADD COLUMN preferred_language TEXT;
ALTER TABLE accounts ADD COLUMN agreement_text TEXT;
