# TB Loan Apps — production-fixed source

This workspace contains the Android apps for the Ghana phone-financing project:

- `customer-app/` — Device Policy Controller (DPC) installed during Android Setup Wizard.
- `agent-app/` — dealer/field-agent enrollment and QR workflow.
- `.github/workflows/` — stable-key release build, APK verification, immutable R2 publishing.
- `scripts/verify-provisioning-source.py` — source-level provisioning contract check.
- `scripts/verify-provisioning-apk.sh` — signed-APK manifest/package/signature check.

## The Samsung Setup Wizard failure fixed here

The previously published customer APK did not expose the two Android 12+ DPC activities required by Setup Wizard:

- `android.app.action.GET_PROVISIONING_MODE`
- `android.app.action.ADMIN_POLICY_COMPLIANCE`

The fixed customer app adds minimal, synchronous handlers for both actions and stores the QR-delivered one-time activation data in device-protected storage. Customer version is now `1.0.2` (`versionCode 3`).


## Customer release and update lifecycle

This pass adds the missing end-of-loan lifecycle:

1. When a final payment clears, the dashboard marks `release_approved=1`, clears dealer lock, and pushes the next due date far into the future.
2. For testing or manual settlement, a dealer can press **Release customer app** in the dashboard/agent app; early release requires an explicit `allowEarlyRelease=true` API request.
3. On the next heartbeat/app refresh/boot, the customer app reports `/api/device/release-complete`, clears Device Owner restrictions where Android permits it, and shows a final screen with **Remove TB User**.
4. After management removal succeeds, Android's normal uninstall confirmation is opened for the customer.

Auto-update support is also included. The customer app schedules a 12-hour WorkManager job that calls `/api/device/app-update`, downloads the HTTPS APK from the current R2 `latest.json`, verifies the SHA-256 base64url checksum, and commits a package installer update. The reliable production channel is still the Android Enterprise / managed-device app-update path; this in-app updater is a fallback for your direct APK/R2 deployment and may show an approval prompt on devices that are not fully managed.

## Release invariants

1. The package must remain `com.touchbase.user`.
2. The device-admin component must remain:
   `com.touchbase.user/com.touchbase.user.admin.SecurePayDeviceAdminReceiver`.
3. Every production update must use the same release keystore. Losing or changing that key breaks managed-device upgrades.
4. Never place an old APK in the dashboard or generate QR payloads from a mutable `latest.apk` URL.
5. The customer APK must be published first; the dashboard reads R2 `latest.json` and pins each QR to the exact immutable APK SHA-256.
6. Never commit `.env`, `.jks`, APKs, signing passwords, JWT secrets, HMAC secrets, or R2 credentials.

## GitHub release secrets

Configure these repository secrets before running **Build & Sign SecurePay APKs**:

- `SIGNING_KEY` — base64 of the permanent release JKS.
- `KEY_STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD` — optional when the same as store password.
- `SIGNING_CERT_HASH` — SHA-256 certificate digest, hexadecimal; colons are accepted.
- `HMAC_SECRET` — must exactly match the dashboard worker secret.
- `R2_ACCESS_KEY_ID`
- `R2_SECRET_ACCESS_KEY`
- `R2_ENDPOINT`
- `R2_BUCKET`
- `R2_PUBLIC_URL` — HTTPS public base URL for the bucket/custom domain.

Example commands for the release-key owner:

```bash
base64 -w0 release.jks > release.jks.base64
keytool -list -v -keystore release.jks -alias YOUR_ALIAS
```

Keep the JKS and recovery copy offline. Do not create a new key for each build.

## Validation

Source contract:

```bash
python3 scripts/verify-provisioning-source.py
```

After CI produces the signed customer APK and Android build-tools are installed:

```bash
scripts/verify-provisioning-apk.sh path/to/customer-release.apk \
  com.touchbase.user \
  com.touchbase.user.admin.SecurePayDeviceAdminReceiver \
  EXPECTED_CERT_SHA256_HEX
```

The CI workflow also refuses to publish when the package, signing certificate, mandatory manifest actions, or public APK checksum do not match.

## Samsung A07 clean retest

1. Deploy the fixed dashboard and apply its migration.
2. Build/sign/publish the fixed customer APK through CI.
3. Confirm R2 contains the immutable APK plus `latest.json`.
4. Create a fresh inventory/account entry and generate a **new** QR in the agent app.
5. Factory-reset the Samsung. A previously failed Device Owner transaction must not be reused.
6. On the first Welcome screen, tap six times, scan the new QR, and keep the phone powered and online.
7. Agent status should move from `pending` to `provisioned`, then `activated`.

Do not test with an old QR: it contains an old one-time token and an APK checksum tied to the old binary.

## Important security boundary

Android Device Owner is the supported way to prevent ordinary uninstall, settings removal, safe-mode removal, and casual factory reset from within Android. It is not a promise against bootloader exploits, signed-firmware/service-center reflashing, hardware attacks, or future OEM vulnerabilities. Do not implement hidden persistence, exploit-based survival, or unauthorized surveillance.

The current code still contains a shared HMAC secret in both Android clients. It is materially improved by nonce replay protection and one-time provisioning credentials, but a later production-hardening phase should replace the shared APK secret with per-device server-issued credentials backed by hardware keystore/attestation.
