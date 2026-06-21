# SecurePay production provisioning fix notes

## What changed

This patch makes Android Enterprise QR provisioning finalize through the Android 12+ `ADMIN_POLICY_COMPLIANCE` path instead of relying on `PROVISIONING_SUCCESSFUL` alone.

Key app-side changes:

- Added `ProvisioningFinalizer.kt` as one shared, crash-resistant finalization path.
- `PolicyComplianceActivity` now verifies Device Owner state and applies base loan security locally during Setup Wizard compliance.
- `GetProvisioningModeActivity` now explicitly returns fully managed Device Owner mode and preserves QR admin extras.
- `ProvisioningActivity` and `SecurePayDeviceAdminReceiver` now use the same finalizer as fallbacks.
- Provisioning components are direct-boot aware.
- Removed `BIND_DEVICE_ADMIN` from the `PROVISIONING_SUCCESSFUL` activity for compatibility with the Android Enterprise/TestDPC pattern.
- Added R8/ProGuard keeps for the provisioning reflection entry points.
- Bumped TB User to versionCode 5 / versionName 1.1.1.
- Hardened the app's uncaught-exception handler so background-thread errors during early DPC launch do not kill provisioning.

## Required production rollout order

1. Push this apps source.
2. Run the GitHub APK workflow and confirm it publishes the new TB User APK to R2.
3. Deploy the patched dealer dashboard source.
4. Generate a brand-new QR code after both APK and dashboard are updated.
5. Factory-reset the Samsung test phone and scan the new QR from Setup Wizard.
6. Before testing lock/uninstall/factory-reset restrictions, verify Device Owner:

```bash
/c/Users/Acer/AppData/Local/Android/Sdk/platform-tools/adb.exe shell dpm is-device-owner com.touchbase.user
```

Expected output must indicate `true`.

If Device Owner is not true, the phone is not in the valid MDM/financing state and all Device-Owner-only controls must be considered untested.

## Important limitation

No Android app can honestly promise to be impossible to bypass. This project should be positioned as Android Enterprise Device Owner protection for consented financed/company-owned devices, not as spyware or covert anti-user control.
