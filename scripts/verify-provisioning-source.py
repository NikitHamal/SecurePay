#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
manifest = root / "customer-app/app/src/main/AndroidManifest.xml"
text = manifest.read_text(encoding="utf-8")
required = [
    "android.app.action.GET_PROVISIONING_MODE",
    "android.app.action.ADMIN_POLICY_COMPLIANCE",
    "android.app.action.PROVISIONING_SUCCESSFUL",
    "android.app.action.DEVICE_ADMIN_ENABLED",
]
missing = [action for action in required if action not in text]
if missing:
    print("Missing manifest actions:", *missing, sep="\n  ", file=sys.stderr)
    raise SystemExit(1)

expected_files = [
    root / "customer-app/app/src/main/java/com/touchbase/user/admin/GetProvisioningModeActivity.kt",
    root / "customer-app/app/src/main/java/com/touchbase/user/admin/PolicyComplianceActivity.kt",
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

print("Provisioning source checks passed")
