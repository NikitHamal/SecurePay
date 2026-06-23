# SecurePay production provisioning fix notes

## What changed in this pass

This patch hardens the Android Enterprise QR provisioning path for Samsung/Android 16 by keeping the QR payload minimal and making the Android 12+ DPC handoff deterministic.

Key app-side changes:

- `GetProvisioningModeActivity` is now a minimal, no-UI Device Owner selector. It returns `PROVISIONING_MODE_FULLY_MANAGED_DEVICE` only when that mode is allowed by Setup Wizard, preserves admin extras, and no longer returns optional education-skip extras.
- `PolicyComplianceActivity` now runs the shared `ProvisioningFinalizer` during the Android 12+ `ADMIN_POLICY_COMPLIANCE` handoff, confirms Device Owner/Admin state, applies base loan security when Device Owner is active, then returns `RESULT_OK` without UI/network work.
- Android 12+ provisioning mode handlers are API-gated with `android:enabled="@bool/provisioning_mode_handlers_enabled"` using `values-v31=true` and base `values=false`.
- TB User version bumped to `versionCode 7` / `versionName 1.1.3` so the APK and QR metadata cannot be confused with earlier failing builds.
- Provisioning source audit script updated for the minimal Samsung QR contract.

## Dashboard-side changes

- QR payload version bumped to `5`.
- The initial QR payload now includes only the Samsung/Android Enterprise essentials: Device Admin component, HTTPS APK URL, APK package SHA-256 checksum, `LEAVE_ALL_SYSTEM_APPS_ENABLED`, optional Wi-Fi fields, and `ADMIN_EXTRAS_BUNDLE`.
- Removed redundant `PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM` from the QR payload. The published manifest may still store it for CI identity checks, but QR enrollment only needs the package checksum; avoiding two checksum gates reduces Samsung Setup Wizard failure risk when any manifest value is stale.
- Removed `PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE` from the QR payload to avoid optional Samsung parser/installed-version edge cases during factory-reset enrollment.

## Required production rollout order

1. Push this apps source.
2. Run the GitHub APK workflow and confirm R2 `latest.json` points to TB User `versionCode 7` / `versionName 1.1.3`.
3. Deploy the patched dashboard source.
4. Generate a brand-new QR code after both APK and dashboard are updated. Do not reuse old QR screenshots.
5. Factory-reset the Samsung test phone and scan the new QR from Setup Wizard.
6. Before testing lock/uninstall/factory-reset restrictions, verify Device Owner:

```bash
/c/Users/Acer/AppData/Local/Android/Sdk/platform-tools/adb.exe shell dpm is-device-owner com.touchbase.user
```

Expected output must indicate `true`.

If Device Owner is not true, the phone is not in the valid financing state; Device-Owner-only controls such as factory-reset block, uninstall restrictions, FRP, and lock task are not validly testable.
