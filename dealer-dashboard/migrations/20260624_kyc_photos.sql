-- Migration to add KYC photo paths to accounts table
ALTER TABLE accounts ADD COLUMN customer_photo_path TEXT;
ALTER TABLE accounts ADD COLUMN national_id_front_path TEXT;
ALTER TABLE accounts ADD COLUMN national_id_back_path TEXT;
