# Touch Base Android apps

This directory contains the two Android clients for the Touch Base device-financing system:

- `agent-app/` — KYC, inventory allocation, customer enrollment, loan-plan assignment, provisioning and agent-scoped sales/payment views.
- `customer-app/` — Android Device Policy Controller (DPC), payment status, overdue lock screen, visible stolen-device tracking, heartbeat and managed application updates.

The backend and administration console are in `../dashboard/`.

## Supported operating model

The production tenancy model is:

```text
Super Admin
  -> DSL Agency
     -> Branch Admin
        -> Agent
           -> Customers / devices / sales / payments
```

The server enforces scope on every protected resource. Agents can see only records they enrolled; branch administrators can see their branch; agency owners can see their agency; Super Admin can see the whole platform.

## Security boundary

The customer app uses the standard Android Enterprise Device Owner/DPC path. It can apply managed-device restrictions, allowlisted lock task mode, scheduled heartbeat, a visible foreground location service after a device is marked stolen, and policy-controlled APK updates.

It does **not** claim persistence against bootloader unlocking, OEM service tools, custom firmware, hardware attacks or a full reflash. Do not ship a rooted or hidden-surveillance build as a substitute for an OEM-supported financed-device program. See `../SECURITY_AND_COMPLIANCE_NOTES.md`.

## Configuration

Never place production secrets in source control. Copy each example file to `local.properties` or set matching environment variables.

### Agent app

```properties
TB_DEBUG_API_BASE_URL=http://10.0.2.2:5173/api/
TB_API_BASE_URL=https://your-dashboard.example/api/
TB_HMAC_SECRET=replace-with-rotated-bootstrap-secret
TB_SIGNING_CERT_HASH=sha256-cert-digest-hex-or-base64url
TB_SUPPORT_PHONE=+233XXXXXXXXX
TB_SUPPORT_WHATSAPP=233XXXXXXXXX
TB_SUPPORT_EMAIL=support@example.com
```

### Customer app

The customer app uses the same four settings plus Firebase values:

```properties
TB_FCM_PROJECT_ID=your-firebase-project
TB_FCM_API_KEY=your-firebase-web-api-key
TB_FCM_SENDER_ID=your-sender-id
TB_FCM_APPLICATION_ID=your-android-app-id
TB_SUPPORT_PHONE=+233XXXXXXXXX
TB_SUPPORT_WHATSAPP=233XXXXXXXXX
TB_SUPPORT_EMAIL=support@example.com
```

`TB_HMAC_SECRET` is only the bootstrap secret. Successful activation requires the original provisioning token, six-digit code and exact 15-digit IMEI, then returns a distinct per-device secret used for registered device traffic.

Customer recovery login is bound to the registered phone-number account, a server-hashed PIN and the exact inventory IMEI. The dealer sees a temporary PIN once during enrollment and can rotate it from the customer detail screen. Recovery login is available only after the DPC has been provisioned on that same device again.

## Build

Open each Android project in Android Studio, use JDK 17, install the Android SDK requested by Gradle, and build with the permanent release signing key.

```bash
cd agent-app
./gradlew :app:assembleRelease

cd ../customer-app
./gradlew :app:assembleRelease
```

The release build intentionally fails when required production values are missing. Use one permanent signing key: the configured signing-certificate hash, anti-tamper check, update pipeline and installed package must all refer to that same certificate.

## ADB provisioning for controlled deployment/testing

Deploy the dashboard and create a fresh provisioning token first, then run:

```bash
./scripts/adb-provision-customer.sh \
  ./customer-app/app/build/outputs/apk/release/app-release.apk \
  '<PROVISIONING_TOKEN>' \
  '<6_DIGIT_CODE>' \
  '<DEVICE_IMEI>'
```

The correct Device Owner component is:

```text
com.touchbase.securepay.client/com.touchbase.user.admin.SecurePayDeviceAdminReceiver
```

The script refuses incomplete arguments, verifies that ADB sees exactly one device, installs the APK, sets Device Owner and launches the activation flow. A factory reset is normally required before assigning Device Owner.

## Before field deployment

1. Deploy the backend and migrations first.
2. Rotate JWT, bootstrap HMAC, Didit webhook and Firebase credentials.
3. Create the initial Super Admin with `../dashboard/scripts/create-super-admin.mjs`.
4. Build the release APKs with the permanent key and publish the customer APK plus `latest.json` to R2.
5. Run the acceptance matrix in `../DEPLOYMENT_RUNBOOK.md` on the exact Samsung/Android models to be sold.
6. Test activation, payment, overdue lock, Wi-Fi/mobile-data access while locked, emergency dialer, stolen tracking, reboot enforcement, update and final release.

## Current verification status

All 109 Kotlin source files and 6 Gradle Kotlin DSL files were scanned for syntax diagnostics, and the backend was type-checked/built during the production pass. Android APK compilation and physical-device behavior still require Android Studio/CI and real-device testing; they were not possible in the audit container.

Start with `../PRODUCTION_AUDIT_2026-07-13.md`, `../RELEASE_CHECKLIST_48H.md` and `../DEPLOYMENT_RUNBOOK.md`.
