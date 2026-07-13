# TB User / TB Agent Production Audit and Recovery Pass

**Audit date:** 13 July 2026  
**Scope:** Android customer DPC, Android dealer/agent app, Cloudflare/Svelte dashboard API, CI release workflow, uploaded runtime logs, and supplied M-KOPA UI references.

## Executive conclusion

The supplied system already had the core pieces of a financed-device platform, but it was not ready to promise reliable reset recovery or a quiet production experience. The most urgent defects were:

1. The agent recovery action crashed before the request was sent because Retrofit could not serialize a `Map<String, Any>` request body.
2. There was no real customer account-number/PIN recovery flow bound to the financed phone.
3. Firebase values were passed under names that the Android Gradle build did not read, so FCM was silently skipped.
4. stolen-device tracking used a foreground notification path that could repeatedly alert, and background workers treated normal cancellation as an error.
5. network callbacks could launch duplicate heartbeat calls.
6. the DPC declared no `disable-keyguard-features` policy even though code used that policy.
7. the release network configuration contained brittle certificate pins that could break when Cloudflare rotated its edge certificate.
8. sensitive local deployment files and a release keystore were present in the submitted trees and must not be redistributed or trusted without rotation.

The production pass fixes those code defects and adds the requested More/Contact screens, customer recovery login, one-time credential handoff, dealer PIN reset, quieter stolen-device tracking, and CI corrections.

The dashboard now passes type checking and its production build. The Android source received a syntax/static pass, but an APK could not be compiled in this audit container because the Android SDK and Gradle distribution were unavailable and outbound dependency downloads were blocked. A signed APK and physical Samsung test are still mandatory before delivery.

## What was changed

### 1. Agent app Retrofit crash fixed

**Observed error**

`Unable to create @Body converter for java.util.Map<java.lang.String, java.lang.Object>`

**Root cause**

The API method used `Map<String, Any>` for a Kotlin Serialization Retrofit request body. The converter cannot reliably create a serializer for arbitrary `Any` values.

**Fix**

Typed, serializable request DTOs now replace the dynamic maps:

- `UpdateAccountRequest`
- `ReleaseAccountRequest`

The repository and customer-detail screen now call those typed endpoints. JSON is configured with `explicitNulls = false`, so optional fields omitted by the app do not overwrite server data with unwanted null values.

### 2. Customer account-number and PIN recovery added

A complete recovery path was added for a DPC that has been provisioned again after an authorized reset:

- the customer's normalized registration phone number becomes the account number;
- the server generates an eight-digit temporary PIN with a cryptographic random source;
- only a bcrypt hash is stored;
- the initial PIN is returned once to the enrolling dealer;
- the dealer can rotate the PIN from the customer detail screen;
- the customer app has an M-KOPA-style account-number/PIN login screen;
- login requires the account number, PIN, and exact 15-digit inventory IMEI;
- a successful login rotates and returns a new per-device HMAC secret;
- generic failures prevent easy account/IMEI enumeration;
- customer-login attempts are rate-limited.

New D1 migration:

`dashboard/migrations/20260713_customer_recovery_login.sql`

This migration must be applied before deploying either updated Android app.

### 3. Reset/reprovision behavior corrected

The customer app now starts in one of three states:

1. registered device -> dashboard;
2. fresh dealer provisioning extras -> activation-code screen;
3. re-provisioned device without an active local session -> customer recovery login.

The login is intentionally disabled when the installation has no trusted expected IMEI. This prevents someone from installing the APK on another phone and using stolen credentials. It also means the DPC must be provisioned again through a supported Android Enterprise/OEM enrollment path after a reset.

### 4. Agent More and Contact screens added

The requested M-KOPA-inspired screens were added to the agent app:

- five-tab bottom navigation;
- More screen;
- Payment Details shortcut;
- app version/build;
- feedback email;
- theme/settings;
- legal notice;
- Contact us;
- logout confirmation;
- Chat, WhatsApp, Call and Email contact actions.

Support details are injected at build time:

- `TB_SUPPORT_PHONE`
- `TB_SUPPORT_WHATSAPP`
- `TB_SUPPORT_EMAIL`

No client contact details are hard-coded in source.

### 5. Customer More and Help screens added

The customer app now includes:

- Home / Payments / More navigation;
- device and app information;
- notification settings shortcut;
- language placeholder;
- support Help screen;
- Chat, WhatsApp, Call and Email actions using build-time support values.

### 6. Repeated notification and background noise reduced

The submitted logs showed normal coroutine cancellation reported as errors, repeated connectivity callbacks, heartbeat duplication, missing FCM configuration, and a missing keyguard device-admin policy.

Changes:

- the automatic battery-optimization settings prompt was removed from application startup;
- battery optimization is now an explicit user action only;
- stolen-device tracking uses a new low-importance, silent notification channel;
- the notification is ongoing but only alerts once and has no vibration/badge;
- the service is `START_NOT_STICKY`;
- tracking and heartbeat workers rethrow cancellation instead of logging it as a failure;
- network failures are logged as deferred/warning conditions;
- connectivity-triggered heartbeat calls are debounced for 20 seconds;
- only one connectivity heartbeat may run at a time;
- a duplicate account-state tracking collector was removed.

A foreground location service still must show an ongoing Android notification while active. It can be quiet, but it cannot be safely or compliantly hidden.

### 7. FCM release configuration fixed

The logs repeatedly reported that Firebase was skipped. The CI workflow exported `FCM_PROJECT_ID`, `FCM_API_KEY`, `FCM_SENDER_ID`, and `FCM_APPLICATION_ID`, while Gradle reads the `TB_FCM_*` names.

The workflow now exports:

- `TB_FCM_PROJECT_ID`
- `TB_FCM_API_KEY`
- `TB_FCM_SENDER_ID`
- `TB_FCM_APPLICATION_ID`

Support-contact secrets are also forwarded to both app builds.

### 8. Device-admin policy declaration fixed

`device_admin.xml` now declares `disable-keyguard-features`, matching calls made by the DPC. This removes the warning that Android denied keyguard hardening/restoration because the admin receiver did not declare the policy.

### 9. Network security made resilient

Release builds now use the Android system trust store and reject cleartext traffic. The previous release certificate pins were brittle because a Cloudflare certificate rotation could stop all API traffic even when the server remained valid.

Debug-only network-security overrides allow the emulator/local test addresses. These overrides are isolated under `src/debug` and do not affect release builds.

## Factory reset: what is and is not possible

A normal third-party APK does not survive a full factory reset and cannot simply appear before Android Setup Wizard by itself.

For the requested customer experience, the phone must be re-enrolled as a managed Device Owner during setup through one of these paths:

- Android Enterprise QR provisioning;
- NFC provisioning where supported;
- Android zero-touch enrollment for eligible reseller/devices;
- Samsung Knox Mobile Enrollment for supported Samsung stock;
- an OEM/preload partnership.

EFRP/Factory Reset Protection policy can control which authorized accounts are accepted during recovery, but it does not reinstall the TB DPC after a reset. The immediate two-day deliverable should therefore use QR provisioning and document the recovery steps. For a commercial Ghana rollout, Samsung KME or zero-touch is the operational target.

## Security and privacy findings

### Critical deployment actions

1. **Rotate all submitted secrets.** The submitted dashboard contains local `.env` and `.dev.vars` files. Their values were not copied into the deliverables, but any credentials contained there should be treated as exposed.
2. **Replace the submitted release keystore.** `apps/securepay-release.jks` was present in the upload. Use a new permanent production key held in a secret manager/GitHub Actions, unless this key was already protected and intentionally shared through a secure channel. Do not lose the permanent production key after rollout.
3. **Do not consider the APK bootstrap HMAC a private secret.** An APK can be reverse engineered. The bootstrap HMAC protects protocol integrity but should not be the sole authentication factor. Recovery also requires PIN + exact IMEI and rate limiting; registered traffic uses a rotated per-device secret.
4. **Obtain Ghana-specific legal review and customer consent.** Device locking, location collection for stolen devices, retention, disclosure, dispute handling and final management release must be clearly stated in the financing contract and privacy notice.
5. **Never silently track a normal customer.** The service is designed to start only when the server marks the device stolen and to stop after recovery or release.

### Additional recommendations

- require a dealer identity check before PIN reset;
- add server-side audit events for PIN generation/reset and recovery login;
- add account lockout/backoff keyed by account and IMEI in addition to IP rate limits;
- send a customer SMS when a PIN is issued or changed, without sending the PIN in plaintext unless the channel and consent model are approved;
- encrypt sensitive KYC fields at rest and define retention/deletion policy;
- add Sentry/Crashlytics or equivalent with redaction; never upload PINs, full Ghana Card numbers, exact IMEIs, HMAC secrets or precise location in crash logs;
- add key rotation and per-device revocation;
- use Play Integrity/device attestation as a supplementary signal, not as the sole authorization mechanism;
- add an emergency/dispute grace workflow so a mistaken lock cannot strand a customer.

## Deployment order

1. Back up the production D1 database.
2. Apply `20260713_customer_recovery_login.sql` remotely.
3. Rotate/configure Cloudflare secrets and deploy the dashboard/API.
4. Confirm `/api/device/customer-login` is reachable only with valid signed requests.
5. Configure GitHub Actions secrets, including correct `FCM_*` inputs and support contacts.
6. Build both apps using one permanent production signing key.
7. Verify the signed customer APK certificate hash equals `TB_SIGNING_CERT_HASH`.
8. Publish the immutable customer APK and update manifest.
9. Provision a factory-reset test Samsung through QR/KME.
10. Run the acceptance matrix below before handing the build to the client.

## Acceptance matrix

| Test | Expected result |
|---|---|
| New sale | Agent receives account number and one-time temporary PIN after successful enrollment |
| Agent recovery action | No Retrofit converter crash; typed request reaches API |
| Correct recovery login | Account number + PIN + exact IMEI restores account and rotates per-device secret |
| Wrong PIN | Generic failure; no device/account detail disclosed |
| Correct PIN, wrong IMEI | Generic failure |
| Dealer PIN reset | New PIN shown once; old PIN immediately fails |
| Factory reset without managed enrollment | TB app is absent; document this as expected Android behavior |
| QR/KME re-enrollment after reset | DPC becomes Device Owner and recovery login is shown |
| Paid/current account | Device stays usable after server sync |
| Overdue/locked account | Lock task activates but payment, configured network settings and emergency path remain usable |
| Mark stolen | Quiet ongoing tracking notification appears; location uploads start |
| Recover stolen device | Tracking service and notification stop |
| App opened normally | No automatic battery-optimization settings popup |
| Network toggled repeatedly | No heartbeat storm; one debounced request at a time |
| Offline heartbeat | Cached state is used; cancellation is not reported as an application error |
| FCM | Firebase initializes with `TB_FCM_*`; token registers and push test succeeds |
| Reboot | Device-owner policy and local status are re-applied once without UI loops |
| Loan fully released | Device management restrictions are removed and server release completion is recorded |

## Verification performed

- Dashboard `npm run check`: **passed, 0 errors and 0 warnings**.
- Dashboard `npm run build`: **passed**.
- All 14 SQL migrations applied successfully to an in-memory SQLite database; the three recovery columns exist.
- Both workflow YAML files parsed successfully.
- Modified XML network/device-admin resources parsed successfully.
- `git diff --check`: **passed**.
- Kotlin changed-source syntax scan: no parser-level errors found.
- Android Gradle compilation: **not completed** because this environment has no Android SDK/cached Gradle distribution and cannot download from `services.gradle.org`.
- Physical Device Owner, reset, KME/QR, Samsung lock-task, FCM, telephony and GPS tests: **not possible in this environment** and remain release gates.

## Files intentionally excluded from deliverables

- `.env`, `.dev.vars`, local secret/property files;
- `.jks` and `.keystore` files;
- compiled APK/AAB files;
- build caches and `node_modules`;
- font binaries.

The changed-files package is intended to be overlaid onto the original source tree. The full sanitized Android source archive omits font binaries, so retain the original project's font resources locally.
