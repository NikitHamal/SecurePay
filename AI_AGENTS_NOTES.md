# AI Agent Notes — SecurePay
## READ THIS FIRST before suggesting any steps or fixes

> This document exists because multiple AI agents have given incorrect, impractical, or dangerous advice. Every claim below has been verified against the actual codebase, Samsung/Android documentation, and field testing in Ghana.

---

## 1. Play Protect CANNOT Be Disabled from the Welcome Screen

**Ground truth:** The Android welcome screen (OOBE / Setup Wizard) has no Settings app, no Google app menu, and no way to access Play Protect. Telling the user to "disable Play Protect before scanning the QR" is impossible. The only ways to avoid Play Protect blocking are:

- **Get the APK on Google Play Store** (Internal Testing or higher). Once Google indexes the signing certificate, Play Protect usually stops flagging it within 24–48 hours. This is the ONLY reliable path.
- **Hope the system-level QR provisioning bypasses Play Protect** because the Setup Wizard (system app) installs the APK. This works on some devices but not all.
- **Do not recommend disabling Play Protect from the welcome screen. It is impossible.**

## 2. Remote Logs Are the ONLY Diagnostic Channel

**Ground truth:** The developer (Nikit, Nepal) has no way to run `adb logcat` on the test device in Ghana. The Ghana client is a non-technical phone dealer. The only way to get crash logs or provisioning stage traces is via the app's `SecureLog` remote logging, which POSTs to `/api/device/logs`. This endpoint is intentionally open (no HMAC) so logs can be sent even before the device is activated. **Do not suggest `adb` or any other local debugging method.**

## 3. Samsung Knox Mobile Enrollment (KME) Is Free but Samsung-Only

**Ground truth:** KME is 100% free [1](https://docs.samsungknox.com/admin/fundamentals/knox-licenses/) and bypasses QR fragility + Play Protect, but it only works on Samsung Galaxy devices purchased through participating resellers or manually added via the Knox Deployment App. **The dealer also sells Tecno, Infinix, and other non-Samsung phones. QR-based Android Enterprise provisioning is the ONLY path for those devices.** Therefore, QR provisioning MUST be made robust regardless of KME.

## 4. Device Owner (DO) vs. Device Admin (DA) Is Make-or-Break

**Ground truth:** If the device ends up as Device Admin instead of Device Owner:
- `DISALLOW_FACTORY_RESET` does not work (the user can factory reset from Settings).
- `DISALLOW_UNINSTALL_APPS` does not work (the uninstall button is visible, though the actual uninstall may still be blocked by the admin dialog).
- Lock-task mode (kiosk pinning) does not work.
- FRP policy does not work.
- **Always verify `dpm is-device-owner com.touchbase.securepay.client` returns `true` before testing any lock/restriction feature.**

## 5. Samsung A-Series (Android 14–16 / One UI 6–7) Has Strict DPC Rules

**Ground truth:** Samsung Knox is the strictest Android Enterprise implementation. These are the verified requirements for Samsung A07 / A-series:
- `GetProvisioningModeActivity` and `PolicyComplianceActivity` MUST be declared with `android:resizeableActivity="false"` and `android:launchMode="singleTop"`.
- `PolicyComplianceActivity` MUST NOT touch `DevicePolicyManager` at all. Any DPM read/write inside that callback causes Samsung to abort provisioning with "Something went wrong."
- `PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM` MUST be present in the QR payload for Samsung to trust the downloaded APK.
- `ACTION_PROVISIONING_COMPLETE` (not just `PROFILE_PROVISIONING_COMPLETE`) MUST be in the DeviceAdminReceiver manifest filter.

## 6. The `latest.json` R2 Manifest Is the Source of Truth for QR Payloads

**Ground truth:** The CI workflow (`build-single-apk.yml`) computes the APK SHA-256, the signing-certificate SHA-256, version code, version name, and writes them to `latest.json` in R2. The dealer dashboard reads `latest.json` to build the QR payload. If the CI does not publish a new APK, the dashboard generates QRs pointing to the old APK. **Always verify the CI workflow completed and `latest.json` was updated before testing a new QR.**

## 7. Currency and Region

**Ground truth:** The app is configured for Ghana. Currency is GHS (Ghana Cedi), stored as cents in the database. The dealer is in Ghana. The client is a phone dealer. Do not suggest KES, NGN, or other currencies. The default was changed from KES to GHS in the database migrations.

## 8. Do Not Suggest Dangerous or Impractical Workarounds

**Banned advice:**
- "Ask the Ghana client to install ADB on their laptop" — they cannot do this.
- "Use TeamViewer/AnyDesk to remote into the phone" — welcome screen has no such capability.
- "Root the device to bypass restrictions" — this destroys the entire business model and violates the loan agreement.
- "Use a custom ROM" — not feasible for a commercial phone dealer.
- "Disable Play Protect from the welcome screen" — impossible.
- "Use a debug build on the production phone" — debug builds are not signed with the release keystore; Play Protect will flag them even harder, and the QR checksum will not match.

## 9. HMAC Authentication Is Required for Most Device APIs, Except Logs

**Ground truth:** The `hooks.server.ts` middleware enforces HMAC signatures on all device endpoints EXCEPT `/api/device/logs`, which is intentionally open so unactivated devices can send diagnostic data. If you modify `SecureLog` to add HMAC headers, you will break logging for devices that have not yet activated (no account ID / no device secret). **Do not add HMAC to the log sender.**

## 10. The Package Name Was Renamed from `com.touchbase.user` to `com.touchbase.securepay.client`

**Ground truth:** The `applicationId` in `customer-app/app/build.gradle.kts` is `com.touchbase.securepay.client`. The namespace is `com.touchbase.user`. The DeviceAdminReceiver component in the QR payload is `com.touchbase.securepay.client/com.touchbase.user.admin.SecurePayDeviceAdminReceiver`. Any AI agent suggesting package-name changes must verify both the `applicationId` and the `DEVICE_ADMIN_COMPONENT` constant in `server.ts`.

## 11. Firebase App ID Was Hardcoded and Invalid After Package Rename

**Ground truth:** The original `SecurePayApplication.kt` hardcoded `.setApplicationId("1:${BuildConfig.FCM_SENDER_ID}:android:2d9fc26c70e61185a72dd6")`. This hash was generated for the old package name. After the rename to `com.touchbase.securepay.client`, the Firebase app ID is invalid. The code was fixed to load `FCM_APPLICATION_ID` from `BuildConfig` (populated by CI secret). **Do not hardcode Firebase app IDs.**

## 12. The `customer-app` and `agent-app` Are Separate Gradle Projects

**Ground truth:** They do not share a root `build.gradle`. Each has its own `settings.gradle.kts` and `build.gradle.kts`. The CI uses `working-directory: ${{ inputs.module }}` to run `./gradlew` inside each sub-project. Do not suggest root-level Gradle commands that assume a unified multi-module project.

## 13. Cloudflare D1 Database Schema Migrations Are Manual

**Ground truth:** There is no automatic migration runner. Schema changes (adding tables, columns) are done by running SQL against the D1 database via Wrangler CLI or the Cloudflare dashboard. The app code assumes the schema is already migrated. If a column is missing, the API will throw 500 errors.

## 14. The `SecurePayApplication` Global Crash Handler Must Not Swallow Main-Thread Crashes

**Ground truth:** The uncaught exception handler was designed to log crashes remotely but still propagate main-thread crashes to the system so that Android's crash dialog appears during development. If you make it swallow ALL exceptions, the app will silently freeze during provisioning, making debugging impossible via remote logs.

## 15. Activation Code Is 6 Digits, Numeric Only

**Ground truth:** The regex is `^\d{6}$`. The server generates it with `generateActivationCode()`. The client validates it with the same regex. Do not suggest longer codes, alphanumeric codes, or QR-based activation. The dealer gives the customer a 6-digit number verbally or on paper.

---

*Last updated: July 2, 2026. Commit: `2bec31d` + post-audit fixes.*
