# Agent App Live Location Fix

This patch fixes the stolen account "View Live Location" button.

## What changed

- The button now opens a dedicated live tracking screen instead of re-opening QR provisioning.
- Added `tracking/{accountId}` navigation route.
- Added an improved live location screen that:
  - loads the latest `/api/accounts/{id}/location` result,
  - auto-refreshes every 15 seconds,
  - shows map, latitude, longitude, accuracy, battery, and last update time,
  - provides manual refresh and an "Open Maps" button.
- The in-app map uses OpenStreetMap inside a WebView, so it does not require a Google Maps API key.
- Agent version bumped to 1.1.1 / versionCode 4.

## Files changed

- `agent-app/app/build.gradle.kts`
- `agent-app/app/src/main/java/com/touchbase/agent/ui/navigation/Screen.kt`
- `agent-app/app/src/main/java/com/touchbase/agent/ui/navigation/SecurePayNavHost.kt`
- `agent-app/app/src/main/java/com/touchbase/agent/ui/customers/CustomerDetailScreen.kt`
- `agent-app/app/src/main/java/com/touchbase/agent/ui/tracking/TrackingMapScreen.kt`

## Test steps

1. Build and install the updated Agent app.
2. Log in.
3. Open a customer/account flagged as stolen.
4. Tap "View Live Location".
5. Confirm it opens the Live Location screen, not the QR provisioning screen.
6. Confirm the screen shows the latest phone location if the customer phone has uploaded location logs.
