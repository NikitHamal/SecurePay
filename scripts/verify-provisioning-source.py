#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
manifest = root / "customer-app/app/src/main/AndroidManifest.xml"
manifest_text = manifest.read_text(encoding="utf-8")
required = [
    "android.app.action.GET_PROVISIONING_MODE",
    "android.app.action.ADMIN_POLICY_COMPLIANCE",
    "android.app.action.PROVISIONING_SUCCESSFUL",
    "android.app.action.DEVICE_ADMIN_ENABLED",
]
missing = [action for action in required if action not in manifest_text]
if missing:
    print("Missing manifest actions:", *missing, sep="\n  ", file=sys.stderr)
    raise SystemExit(1)

expected_files = [
    root / "customer-app/app/src/main/java/com/touchbase/user/admin/GetProvisioningModeActivity.kt",
    root / "customer-app/app/src/main/java/com/touchbase/user/admin/PolicyComplianceActivity.kt",
    root / "customer-app/app/src/main/java/com/touchbase/user/admin/ProvisioningFinalizer.kt",
    root / "customer-app/app/src/main/res/values/provisioning.xml",
    root / "customer-app/app/src/main/res/values-v31/provisioning.xml",
]
for path in expected_files:
    if not path.is_file():
        print(f"Missing source file: {path}", file=sys.stderr)
        raise SystemExit(1)

for path in root.rglob("*.kt"):
    data = path.read_bytes()
    if data.startswith(b"\xef\xbb\xbf"):
        print(f"UTF-8 BOM remains: {path}", file=sys.stderr)
        raise SystemExit(1)
    text = data.decode("utf-8")
    if any(ch in text for ch in "“”‘’"):
        print(f"Smart quote remains in Kotlin source: {path}", file=sys.stderr)
        raise SystemExit(1)

if 'android:directBootAware="true"' not in manifest_text:
    print("Provisioning manifest is missing directBootAware components", file=sys.stderr)
    raise SystemExit(1)

if 'android:enabled="@bool/provisioning_mode_handlers_enabled"' not in manifest_text:
    print("Android 12+ provisioning handlers must be API-gated by bool resources", file=sys.stderr)
    raise SystemExit(1)

# Only the DeviceAdminReceiver should require BIND_DEVICE_ADMIN. The Android 12+
# activity handoff must remain callable by OEM Setup Wizard / ManagedProvisioning
# variants during QR enrollment.
activity_blocks = []
for activity_name in ("admin.GetProvisioningModeActivity", "admin.PolicyComplianceActivity"):
    idx = manifest_text.find(activity_name)
    if idx == -1:
        print(f"Missing activity {activity_name}", file=sys.stderr)
        raise SystemExit(1)
    end = manifest_text.find("</activity>", idx)
    activity_blocks.append(manifest_text[idx:end])
for block in activity_blocks:
    if "android.permission.BIND_DEVICE_ADMIN" not in block:
        print("Provisioning handoff activities must require BIND_DEVICE_ADMIN", file=sys.stderr)
        raise SystemExit(1)
    if "android:noHistory" in block:
        print("Provisioning handoff activities must not use noHistory", file=sys.stderr)
        raise SystemExit(1)

server_candidates = [
    root / "dealer-dashboard/src/lib/api/server.ts",
    root.parent / "dashboard/src/lib/api/server.ts",
]
server = next((path for path in server_candidates if path.is_file()), None)
if server:
    server_text = server.read_text(encoding="utf-8")
    if "PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME" in server_text:
        print("QR payload still uses deprecated PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME", file=sys.stderr)
        raise SystemExit(1)
    if "PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM" not in server_text:
        print("QR payload must include the exact APK package checksum", file=sys.stderr)
        raise SystemExit(1)
    if "PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM" in server_text:
        print("Initial QR payload should not include redundant signing-certificate checksum", file=sys.stderr)
        raise SystemExit(1)
    forbidden_initial_qr_extras = [
        "PROVISIONING_SHOULD_LAUNCH_RESULT_INTENT",
        "PROVISIONING_SKIP_EDUCATION_SCREENS",
    ]
    for value in forbidden_initial_qr_extras:
        if value in server_text:
            print(f"Initial QR payload should not include {value}", file=sys.stderr)
            raise SystemExit(1)
else:
    print("Dashboard source not present beside apps zip; skipped dashboard QR source checks")

print("Provisioning source checks passed")
