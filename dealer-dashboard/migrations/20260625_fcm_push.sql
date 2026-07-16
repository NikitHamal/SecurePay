-- Migration to add FCM push notification support
ALTER TABLE accounts ADD COLUMN fcm_token TEXT;
ALTER TABLE accounts ADD COLUMN fcm_token_updated_at INTEGER;
