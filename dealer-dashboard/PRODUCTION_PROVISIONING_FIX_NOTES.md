# SecurePay dashboard provisioning fix notes

## What changed in this pass

This pass reduces Samsung Setup Wizard QR incompatibility risk by keeping the initial QR payload close to Android/Samsung's documented Device Owner provisioning contract.

- QR provisioning payload still pins the exact Device Admin component and immutable APK bytes.
- Deprecated package-name provisioning extra remains removed from the QR payload.
- APK checksum validation remains strict: URL-safe base64 SHA-256 with no padding.
- Optional signing-certificate checksum is still included when present and valid.
- Removed `PROVISIONING_SKIP_EDUCATION_SCREENS` from the initial QR payload; the DPC now returns it only from `ACTION_GET_PROVISIONING_MODE`, where Android documents it as valid.
- Removed `PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT` from the initial QR payload to avoid Samsung Setup Wizard rejecting a result/launch extra during QR parsing/finalization.
- QR payload version bumped to `4`.

## Required production rollout order

1. Publish the new TB User APK first so R2 `latest.json` points at versionCode `6` / versionName `1.1.2`.
2. Deploy this dashboard.
3. Generate new QR codes only after deployment.
4. Do not reuse old QR screenshots; old QR payloads can point to stale APK metadata and old provisioning extras.
