# TB Loan Apps - production-hardened source

This workspace contains the Android apps for the Ghana phone-financing project:

- `customer-app/` - Device Policy Controller (DPC) installed during Android Setup Wizard.
- `agent-app/` - dealer/field-agent enrollment and QR workflow.
- `.github/workflows/` - stable-key release build, APK verification, immutable R2 publishing.
- `scripts/verify-provisioning-source.py` - source-level provisioning contract check.
- `scripts/verify-provisioning-apk.sh` - signed-APK manifest/package/signature check.

## Current production versions

- Customer app: `1.1.0` (`versionCode 4`)
- Agent app: `1.1.0` (`versionCode 3`)

## What this production pass adds

The customer app now applies a stronger financed-device baseline after Device Owner provisioning:

- Enterprise Factory Reset Protection (EFRP) using dealer-configured Google numeric account IDs.
- Factory reset disabled from Settings while the loan is active.
- Developer options/ADB restrictions, USB file-transfer restriction, safe-boot restriction, app-uninstall/app-control restriction, unknown-source install restriction, and account-modification restriction where Android permits it.
- Base loan restrictions stay active even when the customer is current on payments; only the final server-approved release clears them.
- Final settlement/test release clears EFRP and Device Owner/admin state so the customer app can be removed cleanly.
- Backend-issued per-device API secret after activation, replacing the global APK HMAC secret for normal registered device traffic.
- Customer app update checks include device identity so the dashboard can verify requests with the per-device secret.

The agent app now shows whether the generated QR contains an EFRP-enabled policy, so the field tester can confirm the QR is production-grade before scanning.

## Original Samsung Setup Wizard failure fixed here

The previously published customer APK did not expose the two Android 12+ DPC activities required by Setup Wizard:

- `android.app.action.GET_PROVISIONING_MODE`
- `android.app.action.ADMIN_POLICY_COMPLIANCE`

The fixed customer app keeps minimal, synchronous handlers for both actions and stores QR-delivered one-time activation/security data in device-protected storage.

## Release invariants

1. The package must remain `com.touchbase.securepay.client`.
2. The device-admin component must remain:
   `com.touchbase.securepay.client/com.touchbase.user.admin.SecurePayDeviceAdminReceiver`.
3. Every production update must use the same release keystore. Losing or changing that key breaks managed-device upgrades.
4. Never place an old APK in the dashboard or generate QR payloads from a mutable `latest.apk` URL.
5. The customer APK must be published first; the dashboard reads R2 `latest.json` and pins each QR to the exact immutable APK SHA-256.
6. Never commit `.env`, `.jks`, APKs, signing passwords, JWT secrets, HMAC secrets, per-device secrets, or R2 credentials.

## EFRP setup requirement

EFRP does not use normal email strings in the DPC policy. Configure the dealer's authorized Google numeric user/account IDs in the dashboard security-policy card before generating production QRs.

Recommended operational rule:

1. Create permanent dealer/admin Google accounts controlled by the business.
2. Obtain their numeric Google account IDs through an approved admin/People API process.
3. Save those IDs in the dashboard Inventory -> Production security policy card.
4. Generate fresh QRs after every EFRP policy change.
5. On final loan release, the customer app clears EFRP before removing Device Owner/admin state.

## GitHub release secrets

Configure these repository secrets before running **Build & Sign SecurePay APKs**:

- `SIGNING_KEY` - base64 of the permanent release JKS.
- `KEY_STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD` - optional when the same as store password.
- `SIGNING_CERT_HASH` - SHA-256 certificate digest, hexadecimal; colons are accepted.
- `HMAC_SECRET` - must exactly match the dashboard worker secret for bootstrap/provisioning traffic.
- `R2_ACCESS_KEY_ID`
- `R2_SECRET_ACCESS_KEY`
- `R2_ENDPOINT`
- `R2_BUCKET`
- `R2_PUBLIC_URL` - HTTPS public base URL for the bucket/custom domain.

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
  com.touchbase.securepay.client \
  com.touchbase.user.admin.SecurePayDeviceAdminReceiver \
  EXPECTED_CERT_SHA256_HEX
```

The CI workflow also refuses to publish when the package, signing certificate, mandatory manifest actions, or public APK checksum do not match.

## Samsung A07 clean retest

1. Flashing/Odin/bypass methods should stop. Use stock firmware and official OTA only.
2. Push the updated dashboard. For an existing D1 database, apply only the missing migrations after backup/schema review; for a fresh D1 database, run the current `0001_initial.sql` only.
3. Configure the dealer EFRP numeric Google IDs in Inventory -> Production security policy.
4. Build/sign/publish the fixed customer APK through CI.
5. Confirm R2 contains the immutable APK plus `latest.json` for `versionCode 4` or later.
6. Create a fresh inventory/account entry and generate a new QR in the agent app.
7. Confirm the agent app says EFRP is enabled if this is a production-style test.
8. Factory-reset the Samsung. A previously failed Device Owner transaction must not be reused.
9. On the first Welcome screen, tap six times, scan the new QR, and keep the phone powered and online.
10. Agent status should move from `pending` to `provisioned`, then `activated`.

Do not test with an old QR: it contains an old one-time token, old security-policy extras, and an APK checksum tied to the old binary.

## Important security boundary

Android Enterprise Device Owner plus EFRP is the strongest cross-OEM route available to this app without Samsung Knox Guard. It can stop normal uninstall, settings removal, Settings factory reset, many casual recovery-reset attempts, and many semi-technical ADB/developer-options paths. It is not a promise against bootloader exploits, signed-firmware/service-center reflashing, hardware attacks, motherboard swaps, or future OEM vulnerabilities.

Do not implement hidden persistence, exploit-based survival, unauthorized surveillance, or bypass techniques. For Samsung-only financed-device programs that require stronger OEM-backed default-risk controls, add Samsung Knox Guard as a premium layer while keeping this app/backend for loan logic, payments, updates, and release lifecycle.
