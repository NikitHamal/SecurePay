INSERT INTO accounts (
  id, customer_name, national_id, phone_number, device_id, dealer_id, plan_id,
  total_loan_amount, amount_paid, daily_rate, next_payment_due, status,
  locked_by_dealer, down_payment, term_days, currency_code,
  release_approved, release_approved_at, released_at,
  device_hmac_secret, device_hmac_secret_created_at,
  customer_photo_path, national_id_front_path, national_id_back_path,
  fcm_token, fcm_token_updated_at,
  created_at, updated_at
)
SELECT
  id, customer_name, national_id, phone_number, device_id, dealer_id, plan_id,
  total_loan_amount, amount_paid, daily_rate, next_payment_due, status,
  locked_by_dealer, down_payment, term_days, currency_code,
  release_approved, release_approved_at, released_at,
  device_hmac_secret, device_hmac_secret_created_at,
  customer_photo_path, national_id_front_path, national_id_back_path,
  fcm_token, fcm_token_updated_at,
  created_at, updated_at
FROM accounts_old;
