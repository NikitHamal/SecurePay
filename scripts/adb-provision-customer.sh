#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'TXT'
Usage:
  adb-provision-customer.sh <customer-release.apk> <provisioning-token> <activation-code> <15-digit-imei>

Preconditions:
  - The phone is factory-reset / has no Google or work accounts.
  - USB debugging is temporarily enabled for this controlled enrollment.
  - The token/code were generated for this exact IMEI by the deployed dashboard.
TXT
}

if [ "$#" -ne 4 ]; then
  usage >&2
  exit 2
fi

APK=$1
TOKEN=$2
CODE=$3
IMEI=$4
PACKAGE=com.touchbase.securepay.client
ADMIN_COMPONENT=com.touchbase.securepay.client/com.touchbase.user.admin.SecurePayDeviceAdminReceiver
MAIN_COMPONENT=com.touchbase.securepay.client/com.touchbase.user.MainActivity

[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 2; }
[[ "$TOKEN" =~ ^[A-Fa-f0-9]{32,}$ ]] || { echo "Provisioning token format is invalid" >&2; exit 2; }
[[ "$CODE" =~ ^[0-9]{6}$ ]] || { echo "Activation code must be 6 digits" >&2; exit 2; }
[[ "$IMEI" =~ ^[0-9]{15}$ ]] || { echo "IMEI must be exactly 15 digits" >&2; exit 2; }

adb get-state >/dev/null
adb install -r "$APK"

# Device Owner can only be assigned on a clean, eligible device. This is the
# correct component for the current applicationId + receiver class.
adb shell dpm set-device-owner --user 0 "$ADMIN_COMPONENT"
adb shell dpm list-owners

adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$MAIN_COMPONENT" \
  --es provisioningToken "$TOKEN" \
  --es activationCode "$CODE" \
  --es expectedImei "$IMEI"

echo "Enrollment launched. Keep the phone online and complete the 6-digit activation screen."
