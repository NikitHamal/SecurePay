#!/usr/bin/env bash
set -euo pipefail

APK="${1:-}"
MANIFEST_JSON="${2:-}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-34.0.0}"

if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "Usage: $0 path/to/customer.apk [path/to/latest.json]" >&2
  exit 2
fi

AAPT="${AAPT:-${ANDROID_HOME:-}/build-tools/${BUILD_TOOLS_VERSION}/aapt}"
APKSIGNER="${APKSIGNER:-${ANDROID_HOME:-}/build-tools/${BUILD_TOOLS_VERSION}/apksigner}"
for tool in "$AAPT" "$APKSIGNER"; do
  [[ -x "$tool" ]] || { echo "Missing Android SDK tool: $tool" >&2; exit 2; }
done

BADGING="$($AAPT dump badging "$APK" | head -n 1)"
PACKAGE="$(sed -n "s/.*name='\([^']*\)'.*/\1/p" <<<"$BADGING")"
VERSION_CODE="$(sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" <<<"$BADGING")"
VERSION_NAME="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$BADGING")"
[[ "$PACKAGE" == "com.touchbase.securepay.client" ]] || {
  echo "FAIL: expected package com.touchbase.securepay.client, got $PACKAGE" >&2; exit 1;
}

XML="$($AAPT dump xmltree "$APK" AndroidManifest.xml)"
for action in \
  android.app.action.GET_PROVISIONING_MODE \
  android.app.action.ADMIN_POLICY_COMPLIANCE \
  android.app.action.PROVISIONING_SUCCESSFUL \
  android.app.action.DEVICE_ADMIN_ENABLED; do
  grep -F "$action" <<<"$XML" >/dev/null || {
    echo "FAIL: missing required manifest action $action" >&2; exit 1;
  }
done

APK_SHA_HEX="$(sha256sum "$APK" | awk '{print $1}')"
APK_SHA_B64="$(printf '%s' "$APK_SHA_HEX" | xxd -r -p | base64 | tr '+/' '-_' | tr -d '=\n')"
CERT_HEX="$($APKSIGNER verify --print-certs "$APK" \
  | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
  | head -n 1 | tr -d ':' | tr '[:upper:]' '[:lower:]')"

if [[ -n "${EXPECTED_SIGNING_CERT_HASH:-}" ]]; then
  EXPECTED="$(printf '%s' "$EXPECTED_SIGNING_CERT_HASH" | tr -d ':' | tr '[:upper:]' '[:lower:]' | xargs)"
  [[ "$CERT_HEX" == "$EXPECTED" ]] || {
    echo "FAIL: certificate mismatch actual=$CERT_HEX expected=$EXPECTED" >&2; exit 1;
  }
fi

if [[ -n "$MANIFEST_JSON" ]]; then
  python3 - "$MANIFEST_JSON" "$APK_SHA_B64" "$VERSION_CODE" "$VERSION_NAME" <<'PY'
import json, pathlib, sys, urllib.request
source, checksum, version_code, version_name = sys.argv[1:]
if source.startswith(("https://", "http://")):
    with urllib.request.urlopen(source, timeout=30) as r:
        data = json.load(r)
else:
    data = json.loads(pathlib.Path(source).read_text())
assert data["sha256Base64"] == checksum, (data.get("sha256Base64"), checksum)
assert int(data["versionCode"]) == int(version_code), (data.get("versionCode"), version_code)
assert str(data["versionName"]) == version_name, (data.get("versionName"), version_name)
assert str(data["url"]).startswith("https://"), data.get("url")
print("latest.json matches the signed APK")
PY
fi

cat <<OUT
PASS
  package:      $PACKAGE
  version:      $VERSION_NAME ($VERSION_CODE)
  apk sha256:   $APK_SHA_HEX
  QR checksum:  $APK_SHA_B64
  cert sha256:  $CERT_HEX
OUT
