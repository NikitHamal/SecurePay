# SecurePay / TB User Update Notes - 2026-07-06

This updated package focuses on the real functional blockers you asked to prioritize: stolen-device tracking, location upload, Device Owner permission support, and migration consistency. It intentionally does not remove your env/build artifacts, and it does not spend time on rate-limiting or public device-log hardening.

## Fixed in dashboard/API

- Added `migrations/20260706_stolen_tracking_location.sql` for:
  - `accounts.is_stolen`
  - `location_logs`
  - location/stolen indexes
- Corrected `migrations/0001_initial.sql` so a fresh D1 database can apply all migrations in order without duplicate-column failures.
- Fixed `/api/device/location`:
  - requires top-level `accountId` and `imei`
  - accepts single location pings and offline `logs` batches
  - validates latitude/longitude/accuracy/battery/timestamp
  - verifies the account belongs to the IMEI before writing
  - returns stored count and latest ping
- Fixed HMAC identity parsing so location requests can resolve the per-device secret before the route handler reads the body.
- Tightened HMAC fallback so global HMAC is used only on bootstrap endpoints: `check`, `activate`, and `app-update`.
- Added `isStolen` to device-facing `check`, `heartbeat`, `account`, and `activate` responses.
- Stolen accounts now return status `STOLEN` to customer apps.
- Flagging stolen now also sets `locked_by_dealer = 1`, so the device locks on the next sync.
- Force-unlock now refuses while the account is still marked stolen.
- Dealer/agent location reads return both `latitude/longitude` and `lat/lng` for compatibility.

## Fixed in customer app

- Replaced runtime-fragile `Map<String, Any>` location upload with typed Kotlin serialization models:
  - `LocationSample`
  - `LocationReportRequest`
- Added persistent SharedPreferences-backed offline location queue:
  - `LocationReportStore`
- `TrackingService` now:
  - includes both `accountId` and `imei` in uploads
  - batches queued pings when network is available
  - reports real battery percentage
  - handles foreground-service API differences cleanly
  - removes location callbacks on shutdown
- `TrackingWorker` now passes `accountId` to `/api/device/check`, allowing per-device HMAC verification after activation.
- `DevicePolicyController` now attempts to grant location/background-location/notification permissions when the app is Device Owner.
- Customer models now understand `isStolen` and treat stolen as locked locally.

## Validation done here

- D1/SQLite migrations were applied locally in filename order successfully.
- Full dashboard build was not run because `node_modules` is not present in the uploaded dashboard package.
- Android compile was not run because the Gradle wrapper attempted to download Gradle and this environment has no internet access.

## Must run on your machine

```bash
cd dashboard
npm install
npm run check
npm run build
```

```bash
cd apps/customer-app
./gradlew :app:assembleRelease
```

Then deploy the dashboard migration and publish a fresh signed customer APK.
