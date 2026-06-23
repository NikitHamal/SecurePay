# SecurePay dashboard provisioning fix notes

## What changed in this pass

This pass keeps the initial QR payload close to Samsung's documented Android Enterprise Device Owner QR contract.

- QR provisioning payload still pins the exact Device Admin component and immutable APK bytes.
- Deprecated package-name provisioning extra remains removed from the QR payload.
- APK checksum validation remains strict: URL-safe base64 SHA-256 with no padding.
- Removed redundant `PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM` from the QR payload; package checksum is sufficient for QR enrollment and avoids a second stale-checksum failure gate.
- Removed optional `PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE` from the QR payload to avoid Samsung installed-version edge cases during repeated setup attempts.
- `PROVISIONING_SKIP_EDUCATION_SCREENS` and `PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT` remain absent from the initial QR payload.
- QR payload version bumped to `5`.

## Required production rollout order

1. Publish the new TB User APK first so R2 `latest.json` points at versionCode `13` / versionName `1.1.9`.
2. Deploy this dashboard.
3. Generate new QR codes only after deployment.
4. Do not reuse old QR screenshots; old QR payloads can point to stale APK metadata and old provisioning extras.
