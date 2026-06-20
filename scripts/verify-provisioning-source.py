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

server = root / "dealer-dashboard/src/lib/api/server.ts"
if not server.is_file():
    print(f"Dashboard server module missing: {server}", file=sys.stderr)
    raise SystemExit(1)
server_text = server.read_text(encoding="utf-8")
if "PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME" in server_text:
    print("QR payload still uses deprecated PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME", file=sys.stderr)
    raise SystemExit(1)
if "PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM" not in server_text:
    print("QR payload should include signing-certificate checksum when available", file=sys.stderr)
    raise SystemExit(1)

print("Provisioning source checks passed")
