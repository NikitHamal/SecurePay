# TB 48-Hour Release Checklist

## First 2 hours — backend first

- [ ] Back up production D1.
- [ ] Rotate exposed/local dashboard secrets.
- [ ] Apply `dashboard/migrations/20260713_customer_recovery_login.sql`.
- [ ] Deploy dashboard/API.
- [ ] Confirm existing agent login, account list and payment APIs still work.

## Hours 2–6 — release configuration

- [ ] Create/confirm one permanent Android release signing key.
- [ ] Configure `SIGNING_KEY`, `KEY_ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD` and exact `SIGNING_CERT_HASH` in GitHub Actions.
- [ ] Configure `HMAC_SECRET` after rotation.
- [ ] Configure `FCM_PROJECT_ID`, `FCM_API_KEY`, `FCM_SENDER_ID`, `FCM_APPLICATION_ID`.
- [ ] Configure Ghana support phone, WhatsApp and email.
- [ ] Run the Android CI workflow for both apps.

## Hours 6–12 — smoke test

- [ ] Install signed agent APK and sign in.
- [ ] Open More and Contact us; test all support actions.
- [ ] Enroll one test customer/device.
- [ ] Record the returned account number and temporary PIN.
- [ ] Confirm agent customer-detail recovery no longer throws the Retrofit converter error.
- [ ] Reset the PIN and confirm the old PIN fails.

## Hours 12–24 — managed-device test

- [ ] Factory-reset the exact Samsung model to be sold.
- [ ] Provision the signed customer DPC through Android Enterprise QR or Samsung KME.
- [ ] Confirm Device Owner state.
- [ ] Log in with account number + PIN on the exact linked IMEI.
- [ ] Test correct credentials, wrong PIN, and wrong-device scenarios.
- [ ] Test payment/current, warning, overdue lock and recovery.
- [ ] Test emergency dialer and the allowed network/settings path while locked.

## Hours 24–36 — background behavior

- [ ] Open/close the app repeatedly: no automatic battery-optimization popup.
- [ ] Mark device stolen: one quiet ongoing location notification, not repeated heads-up alerts.
- [ ] Recover device: tracking notification disappears.
- [ ] Toggle Wi-Fi/mobile data repeatedly: no heartbeat storm.
- [ ] Reboot online and offline.
- [ ] Send an FCM data message and verify token registration.

## Hours 36–48 — client handoff

- [ ] Create a one-page dealer enrollment guide.
- [ ] Create a one-page customer recovery guide.
- [ ] Clearly state that reset recovery requires QR/KME/zero-touch re-enrollment.
- [ ] Export signed APK hashes and signing-certificate digest.
- [ ] Keep production signing key and Cloudflare secrets outside the handoff archive.
- [ ] Obtain written acceptance on the tested Samsung model and Android version.

## Do not claim complete until

- the new database migration is live;
- the signed apps were built by CI;
- recovery was tested after a real factory reset and Device Owner re-provisioning;
- stolen/recovered notification behavior was verified on a physical phone;
- FCM initializes without the missing-BuildConfig warning.
