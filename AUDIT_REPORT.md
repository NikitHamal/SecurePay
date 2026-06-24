# OneMode vs SecurePay — Deep Feature Audit Report

**Audit Date**: 2026-06-24
**Auditor**: AI Deep Audit Agent
**Scope**: Full stack comparison of OneMode (com.android.onemode v8.5) against SecurePay (customer-app + agent-app + dealer-dashboard + backend API)
**Method**: Source code inspection of decompiled OneMode APK (jadx) vs SecurePay production source

---

## 1. Executive Summary

| Dimension | OneMode | SecurePay |
|-----------|---------|-----------|
| **App Type** | Single Android DPC (Device Policy Controller) / EMM | Full stack: Android DPC + Agent App + Web Dashboard + Backend API |
| **Architecture** | Monolithic Android app + single REST backend | Modular multi-app architecture with Turso DB, SvelteKit dashboard, HMAC-signed APIs |
| **Target Use** | Financed device lock/unlock, EMI tracking, anti-theft | Pay-as-you-go smartphone financing (M-KOPA style) |
| **Provisioning** | Android Enterprise DPC (QR/NFC/ADB) | Android 12+ Device Owner via QR code with FRP policy |
| **Security Maturity** | Moderate (hardcoded API key, basic auth) | High (HMAC-SHA256, encrypted storage, APK signing verification, anti-tamper) |
| **Backend Depth** | Single REST API (punontechnologies.com) | Full dealer dashboard, payment ledger, KPI analytics, CI/CD pipeline |
| **Offline Resilience** | Limited (relies on periodic sync) | Strong (offline lock enforcement with monotonic time anti-tampering) |
| **OEM Integration** | Deep (Tecno, Infinix, itel auto-start) | None |

**Overall Assessment**: OneMode is a mature, feature-rich Android device management app with aggressive anti-theft capabilities (hidden camera, SMS interception, SIM tracking, kiosk mode). SecurePay is a more modern, architecturally superior full-stack financing platform with stronger security primitives (HMAC signing, encrypted storage, APK integrity verification) and a complete dealer/agent/customer ecosystem. SecurePay's primary gaps are in advanced device control features (kiosk mode, location tracking, SIM detection, hidden camera) borrowed from traditional MDM solutions.

---

## 2. OneMode Architecture Overview

**Package**: `com.android.onemode`  
**Version**: 8.5 (versionCode 85)  
**Min SDK**: 28 (Android 9.0)  
**Target/Compile SDK**: 34 (Android 14)  
**Backend**: `punontechnologies.com`  
**App Type**: Device Policy Controller (DPC) / Enterprise Mobility Management (EMM)

### Component Summary
- **13 Activities**: Splash, Registration, AntiTheft (dashboard), Kiosk, CameraPreview, SimTracking, Alert, FetchLocation, Advertise, PrivacyPolicy, DownloadApp, Leanback (TV), TroubleShoot, plus ProvisioningMode and AdminPolicyCompliance for Android Enterprise
- **7 Services**: ShutDown, LocationForeground, YourForeground (boot sync), UpdateApp, AntiApp, SendLocationPhoto, MyFirebaseMessaging (FCM)
- **12 Broadcast Receivers**: SimChanged, Boot, Shutdown, SMS, ApkDownload, AntiDownload, ApkUpdateComplete, ApkInstalled, LocationAlarm, SyncAlarm, AutoLockAlarm, DateChange
- **40+ Permissions**: Dangerous level permissions for location, telephony, SMS, contacts, camera, storage; signature-level for install/delete packages and manage device policies

**Data Layer**: Room database (`onemode_database`) with `EmiDetails` and `SyncData` entities. Retrofit 2 API client with hardcoded API key (`be2cb91913f1e8`).

**Messaging**: Firebase Cloud Messaging with topic-based subscriptions (`ACCOUNT_ID`, `ORGANIZATION_TOPIC_{orgId}`, `FL_VERSION_85`).

---

## 3. SecurePay Architecture Overview

**Packages**: `com.touchbase.user` (customer), `com.touchbase.agent` (agent)  
**Backend**: SvelteKit SPA + Cloudflare Workers + Turso (libSQL) database + Cloudflare R2 storage  
**App Type**: Full-stack financed-device platform

### Component Summary
- **Customer App**: 4 Activities (Main, Provisioning, PolicyCompliance, GetProvisioningMode), plus LockTaskActivity. 2 Workers (Heartbeat 15min, AppUpdate 12hrs). 2 Receivers (Boot, DeviceAdmin). FCM service.
- **Agent App**: Single-activity Compose app with 10+ screens (Login, EnrollmentWizard, Dashboard, Inventory, Ledger, Settings, etc.)
- **Dealer Dashboard**: SvelteKit SPA with 6 routes (Overview, Login, Customers, Inventory, Ledger, Logs)
- **Backend API**: 28 endpoints split across dealer-auth (JWT), device-auth (HMAC), and public provisioning
- **Database**: 9 tables (dealers, devices, accounts, plans, payments, lock_events, sessions, hmac_nonces, provisioning_tokens)

**Security**: HMAC-SHA256 request signing with per-device secrets, EncryptedSharedPreferences (AES-256-GCM), APK signing certificate allowlist, root/emulator/tamper detection, monotonic time anti-tampering.

**Provisioning**: Android 12+ QR-based Device Owner provisioning with EFRP (Factory Reset Protection) policy embedding, activation code exchange for HMAC secret.

---

## 4. Feature Comparison

### 4.1 Android — Activities

| Feature | OneMode | SecurePay | Notes |
|---------|---------|-----------|-------|
| Splash / Launcher | ✅ `SplashActivity` (exported) + alias | ✅ `MainActivity` (exported) | SecurePay has direct boot awareness |
| Registration flow | ✅ `RegistrationActivity` | ✅ `ActivationScreen` (Compose) | OneMode has built-in admin activation; SecurePay uses 6-digit activation code after provisioning |
| Main dashboard | ✅ `AntiTheftActivity` | ✅ `DashboardScreen` (Compose) | Both show EMI/payment status |
| Kiosk mode | ✅ `KioskActivity` (singleInstance) | ❌ No kiosk UI | SecurePay only has lock overlay; OneMode has dedicated kiosk with payment/controls |
| SIM tracking | ✅ `SimTrackingActivity` | ❌ No SIM screen | OneMode has dedicated SIM change UI |
| Location display | ✅ `FetchLocationActivity` | ❌ No location screen | OneMode fetches and displays GPS |
| Camera / theft evidence | ✅ `CameraPreviewActivity` | ❌ No camera capture | OneMode captures hidden photos on lock events |
| Privacy policy | ✅ `PrivacyPolicyActivity` | ❌ No in-app policy | OneMode includes built-in privacy display |
| Leanback / TV | ✅ `LeanbackActivity` | ❌ No TV support | OneMode supports Android TV |
| Troubleshooting | ✅ `TroubleShootActivity` (deep link) | ❌ No troubleshooting screen | OneMode has deep link to `mdmsolutions.co.in` |
| Android Enterprise provisioning | ✅ `ProvisioningModeActivity`, `AdminPolicyComplianceActivity` | ✅ `ProvisioningActivity`, `PolicyComplianceActivity`, `GetProvisioningModeActivity` | Both support Android 12+ DO provisioning |
| Lock overlay | ❌ Basic lock | ✅ `LockTaskActivity` + `LockOverlayScreen` | SecurePay has imperative, full-screen Compose lock with emergency dial |
| Advertising | ✅ `AdvertiseActivity` | ❌ No ads | OneMode shows promotional content |

### 4.2 Android — Services & Background Work

| Feature | OneMode | SecurePay | Notes |
|---------|---------|-----------|-------|
| Shutdown listener | ✅ `ShutDownService` (foreground, specialUse) | ❌ No shutdown service | OneMode reports shutdown to server; SecurePay relies on boot receiver |
| Location tracking | ✅ `LocationForegroundService` (foreground, location) | ❌ No location service | OneMode actively tracks GPS; SecurePay has no location feature |
| Boot sync | ✅ `YourForegroundService` (foreground, dataSync) | ❌ No boot foreground service | OneMode creates persistent boot notification |
| Photo upload | ✅ `SendLocationPhotoService` (foreground, dataSync) | ❌ No photo upload service | OneMode sends theft evidence to server |
| App updates | ✅ `UpdateAppService` | ✅ `AppUpdateWorker` (WorkManager, 12hr) | Both handle APK updates; SecurePay uses WorkManager instead of Service |
| Anti-app download | ✅ `AntiAppService` | ❌ No anti-app service | OneMode downloads companion anti-theft app |
| FCM push | ✅ `MyFirebaseMessagingService` | ✅ `FcmService` | Both handle FCM; OneMode has topic subscriptions |
| Periodic sync | ✅ Alarm-based sync (`SyncAlarmReceiver`) | ✅ `HeartbeatWorker` (WorkManager, 15min) | SecurePush uses modern WorkManager; OneMode uses AlarmManager |
| Auto-lock scheduling | ✅ `AutoLockAlarmReceiver` + alarms | ❌ No scheduled lock | OneMode supports date/time-based lock scheduling |
| Location alarms | ✅ `LocationAlarmReceiver` | ❌ No location alarms | OneMode schedules periodic location reports |

### 4.3 Android — Broadcast Receivers

| Feature | OneMode | SecurePay | Notes |
|---------|---------|-----------|-------|
| Boot completed | ✅ `BootReceiver` (priority 999, LOCKED_BOOT_COMPLETED) | ✅ `BootReceiver` (BOOT_COMPLETED, LOCKED_BOOT_COMPLETED) | Both restart services/locks on boot |
| Shutdown | ✅ `ShutdownReceiver` (priority 1000) | ❌ No shutdown receiver | OneMode reports shutdown; SecurePay does not |
| SIM change | ✅ `SimChangedReceiver` (priority 999) | ❌ No SIM receiver | OneMode detects/locks on SIM swap — critical anti-fraud feature |
| SMS interception | ✅ `SMSReceiver` (priority 100) | ❌ No SMS receiver | OneMode intercepts incoming/outgoing SMS |
| App install monitor | ✅ `ApkInstalledReceiver` (PACKAGE_ADDED) | ❌ No install monitor | OneMode watches for sideloaded apps |
| Update completion | ✅ `ApkUpdateCompleteReceiver` (MY_PACKAGE_REPLACED) | ✅ `AppUpdateReceiver` | Both handle self-update completion |
| Download complete | ✅ `ApkDownloadReceiver`, `AntiDownloadReceiver` | ❌ No download receiver | OneMode handles APK downloads |
| Date/time change | ✅ `DateChangeReceiver` (TIME_SET, DATE_CHANGED) | ❌ No date/time receiver | OneMode monitors system clock changes; SecurePay handles this via monotonic time |
| Device admin | ✅ `DeviceAdminReceiver` | ✅ `SecurePayDeviceAdminReceiver` | Both enforce device policies |

### 4.4 Android — Permissions

| Permission | OneMode | SecurePay |
|-----------|---------|-----------|
| Fine/Coarse/Background Location | ✅ All three | ❌ None |
| Read phone state / numbers | ✅ Both | ❌ Neither |
| Call phone | ✅ | ❌ |
| SMS (read/send/receive) | ✅ All | ❌ None |
| Process outgoing calls | ✅ | ❌ |
| Read/write contacts | ✅ Both | ❌ Neither |
| Camera | ✅ | ✅ (agent app only for IMEI scan) |
| Read/write storage | ✅ Both | ❌ Neither |
| Post notifications | ✅ | ✅ |
| Request ignore battery optimizations | ✅ | ✅ |
| Foreground service (location/data-sync/special-use) | ✅ All | ❌ None |
| Schedule exact alarms | ✅ | ❌ |
| Wake lock | ✅ | ✅ |
| System alert window (overlay) | ✅ | ❌ |
| Write settings / Write secure settings | ✅ | ❌ |
| Manage device policy suite | ✅ (18+ policies) | ✅ (13+ policies) |
| Install/delete packages | ✅ Both | ✅ Install only |
| NFC | ❌ | ✅ (customer app) |
| Query all packages | ✅ | ❌ |

### 4.5 Device Policies & MDM

| Policy | OneMode | SecurePay | Notes |
|--------|---------|-----------|-------|
| Device Owner provisioning | ✅ | ✅ | Both support DO; SecurePay uses Android 12+ QR flow |
| Profile Owner provisioning | ✅ | ❌ | OneMode also supports PO |
| Factory reset prevention | ✅ `no_factory_reset` | ✅ `DISALLOW_FACTORY_RESET` + EFRP | SecurePay adds dealer-controlled FRP accounts |
| Safe boot prevention | ✅ `no_safe_boot` | ✅ `DISALLOW_SAFE_BOOT` | Equivalent |
| USB file transfer disable | ✅ `no_usb_file_transfer` | ✅ `DISALLOW_USB_FILE_TRANSFER` | Equivalent |
| Add user restriction | ✅ `no_add_user` | ✅ `DISALLOW_ADD_USER` | Equivalent |
| External storage disable | ✅ `no_physical_media` | ✅ `DISALLOW_MOUNT_PHYSICAL_MEDIA` | Equivalent |
| Debugging disable | ✅ `no_debugging_features` | ✅ `DISALLOW_DEBUGGING_FEATURES` | Equivalent |
| Managed profile restriction | ✅ `no_add_managed_profile` | ❌ No managed profile policy | OneMode is more restrictive |
| Date/time config restriction | ✅ `no_config_date_time` | ✅ Auto time enforced (anti-tamper) | Different approaches; SecurePush uses server-synced time |
| Status bar disable | ✅ (kiosk mode) | ❌ No status bar policy | OneMode hides status bar in kiosk |
| Keyguard disable | ✅ (kiosk mode) | ✅ (when locked) | SecurePay disables keyguard during lock overlay |
| Lock now | ✅ `lockNow()` | ✅ Lock overlay | OneMode uses system lock; SecurePay uses app-level overlay |
| App hiding (Play Store, GMS) | ✅ `setApplicationHidden` | ❌ No app hiding | OneMode hides critical apps |
| Uninstall block | ✅ `setUninstallBlocked` | ❌ No uninstall block | OneMode prevents its own removal |
| Lock task (kiosk) | ✅ `setLockTaskPackages` | ✅ `LockTaskActivity` | Both support kiosk; OneMode has richer kiosk UI |
| System update policy | ✅ `setSystemUpdatePolicy` | ❌ No update policy | OneMode controls OS updates |
| Stay on while plugged | ✅ `setGlobalSetting` | ✅ Enabled when locked | SecurePay prevents charging exploit |
| Persistent preferred activity | ✅ `addPersistentPreferredActivity` | ❌ No persistent activity | OneMode sets default home screen |
| Kiosk mode UI | ✅ Full kiosk with app whitelist | ❌ Lock overlay only | OneMode restricts to single app; SecurePay only overlays lock screen |
| Input method whitelist | ❌ Not found | ✅ Google Latin, Samsung, SwiftKey, QuickKeyboard | SecurePay restricts keyboards |
| Screen capture block | ❌ Not found | ✅ Blocked when locked | SecurePay prevents screenshots during lock |
| Trust agents / fingerprint disable | ❌ Not found | ✅ Disabled when locked | SecurePay biometric bypass protection |

### 4.6 Networking & API

| Feature | OneMode | SecurePay | Notes |
|---------|---------|-----------|-------|
| API client | Retrofit 2 + OkHttp | Retrofit + OkHttp + HmacInterceptor | Both use Retrofit |
| Auth mechanism | Hardcoded API key: `be2cb91913f1e8` | HMAC-SHA256 with per-device secret | SecurePay is cryptographically secure; OneMode uses trivial auth |
| Request signing | ❌ None | ✅ HMAC(body + timestamp + nonce) | SecurePay prevents replay attacks |
| Signature headers | `Authorization`, `appVersion`, `deviceId` | `X-Signature`, `X-Timestamp`, `X-Nonce`, `X-Device-Id` | SecurePay has structured headers |
| Backend base URL | `punontechnologies.com` | Turso + SvelteKit (Cloudflare Workers) | SecurePay is serverless/edge; OneMode uses monolithic backend |
| API endpoint count | ~30 endpoints | 28 endpoints | Comparable |
| Device control endpoints | ✅ Extensive (lock, app-lock, PIN, SIM lock, tracking, restrictions, camera, calls, network, Bluetooth, WiFi, USB, wallpaper, capture, shutdown, reboot, hide/unhide, unclaim) | ✅ Limited (lock/unlock, heartbeat, check, activate, payments, update, release) | OneMode has granular device control; SecurePay focuses on financing lifecycle |
| EMI/loan management | ✅ EMI schedule, penalty, payment reminders | ✅ Payment ledger, due date calculation, plan management | Both handle financed-device lifecycle |
| Retailer/agent API | ✅ `get_user_phone` for retailer contacts | ✅ Full agent authentication and enrollment API | SecurePay has full separate agent app |
| Web dashboard | ❌ None apparent | ✅ Full SvelteKit dealer dashboard | SecurePay has rich web console |

### 4.7 Data Storage

| Feature | OneMode | SecurePay | Notes |
|---------|---------|-----------|-------|
| Database | Room (SQLite) | Turso (libSQL / SQLite) | Both use SQLite derivative |
| Local entities | `EmiDetails`, `SyncData` | `DeviceStatus`, `LoanAccount`, `DeviceSecurityPolicy` | OneMode has EMI schedule; SecurePay has account + policy |
| SharedPreferences | `MySharedPrefs` (plain) | `EncryptedSharedPreferences` (AES-256-GCM) | SecurePay encrypts local storage; OneMode does not |
| Secrets storage | Plain SharedPreferences | EncryptedSharedPreferences | SecurePay protects API secrets |
| Offline data | EMI schedule + sync data | Account status + payments + security policy | Both cache essential data |
| KYC image storage | N/A (agent app presumably handles) | Cloudflare R2 (`kyc/customer_{id}_{type}.jpg`) | SecurePush has formal KYC pipeline |

### 4.8 Push Messaging / FCM

| Feature | OneMode | SecurePay | Notes |
|---------|---------|-----------|-------|
| FCM integration | ✅ `MyFirebaseMessagingService` | ✅ `FcmService` | Both handle FCM |
| Topic subscriptions | ✅ `ACCOUNT_ID`, `ORGANIZATION_TOPIC_{id}`, `FL_VERSION_85` | ❌ No topic subscriptions | OneMode uses topic-based multicast |
| Message deduplication | ✅ `LastMessageId` shared preference | ❌ No dedup mentioned | OneMode handles duplicate FCM messages |
| Token refresh sync | ✅ Updates server via API | ✅ `POST /device/fcm-token` | Both sync tokens to backend |
| Message types | Lock, unlock, config, EMI alerts, SIM change, tracking, wipe | Lock/unlock only (triggered by dealer actions) | OneMode has richer command set via FCM |
| Notification channel | ✅ `high_importance_channel` | ❌ No explicit channel | OneMode manages notification channels |

### 4.9 Security & Anti-Tamper

| Feature | OneMode | SecurePay | Notes |
|---------|---------|-----------|-------|
| Device Admin / DPC | ✅ Full DPC | ✅ Full DPC | Both are device owners |
| Root detection | ✅ Basic (battery opt check) | ✅ Extensive (20+ root paths, `which su`, Magisk) | SecurePay has stronger root detection |
| Emulator detection | ❌ Not found | ✅ Build props + qemu file checks | SecurePay prevents emulator abuse |
| APK tampering detection | ❌ Not found | ✅ SHA-256 signing certificate allowlist | SecurePay verifies APK integrity |
| Install source validation | ❌ Not found | ✅ Rejects non-Play Store chains | SecurePay enforces install source |
| Offline lock enforcement | ❌ Relies on periodic alarms | ✅ Time-synced monotonic anchor + cached policy | SecurePush works offline with tamper-resistant time |
| Clock anti-tampering | ❌ DateChangeReceiver only | ✅ Server-synced monotonic time | SecurePay is robust against clock manipulation |
| Request signing | ❌ Hardcoded API key only | ✅ HMAC-SHA256 with timestamp + nonce | SecurePush prevents replay and MITM |
| Encrypted storage | ❌ Plain SharedPreferences | ✅ AES-256-GCM EncryptedSharedPreferences | SecurePush encrypts credentials |
| FRP (Factory Reset Protection) | ✅ Basic admin restriction | ✅ EFRP with dealer account IDs + `blockFactoryReset` | SecurePush has stronger FRP with dealer recovery |
| Direct boot awareness | ❌ Not found | ✅ `android:directBootAware` | SecurePay handles pre-unlock encryption |
| Crash handling | ❌ Not found | ✅ Defensive crash handling | SecurePush prevents DPC crashes |
| Debug mode det. | ❌ Not found | ✅ Detects debug builds / test keys | SecurePush has debug detection |
| HMAC replay protect. | ❌ None | ✅ Nonce store + 10min window | SecurePush prevents replay attacks |
| USB debugging block | ❌ Not found | ✅ `ADB_ENABLED=0` | SecurePush disables ADB |
| Unknown sources block | ❌ Not found | ✅ `INSTALL_NON_MARKET_APPS=0` | SecurePush blocks sideloading |

### 4.10 Dashboard / Dealer Console

| Feature | OneMode | SecurePay | Notes |
|---------|---------|-----------|-------|
| Web dashboard | ❌ None | ✅ Full SvelteKit SPA | SecurePay has rich web console |
| Login/auth | ❌ N/A | ✅ JWT + bcrypt password | SecurePush has proper auth |
| Customer list | ❌ N/A (backend presumably has) | ✅ Searchable, filterable list with status | SecurePush has customer management |
| KPI analytics | ❌ N/A | ✅ Active/warning/locked counts, outstanding, daily collections | SecurePush has portfolio analytics |
| Payment ledger | ❌ N/A | ✅ Filterable by method, paginated | SecurePush has transaction history |
| Inventory mgmt | ❌ N/A | ✅ IMEI matrix, device table, CRUD | SecurePush has device inventory |
| Real-time logs | ❌ N/A | ✅ Diagnostic log stream with auto-refresh | SecurePush has logging console |
| Force lock/unlock | ❌ N/A | ✅ One-click dealer controls + FCM push | SecurePush has remote commands |
| Payment recording | ❌ N/A | ✅ Extends `nextPaymentDue` automatically | SecurePush handles payment math |
| Customer release | ❌ N/A | ✅ Release approval workflow | SecurePush has payoff workflow |
| Status derivation | ❌ N/A | ✅ `computeStatus()`: ACTIVE → WARNING → LOCKED | SecurePush has clear status rules |

### 4.11 Agent Mobile App

| Feature | OneMode | SecurePay | Notes |
|---------|---------|-----------|-------|
| Separate agent app | ❌ N/A | ✅ `com.touchbase.agent` | SecurePush has dedicated agent app |
| Agent login | ❌ N/A | ✅ JWT-based with Remember Me | SecurePush has auth flow |
| KYC capture | ❌ N/A | ✅ Name, national ID, phone, ID photos, selfie | SecurePush captures full KYC |
| IMEI scanning | ❌ Manual entry only? | ✅ Camera-based barcode scanning | SecurePush has modern scanner |
| Plan selection | ❌ N/A | ✅ Pre-configured plans + custom daily rate | SecurePush has flexible plans |
| QR provisioning | ❌ N/A | ✅ Generates QR with provisioning token + security policy | SecurePush has Android 12+ QR provisioning |
| Customer list | ❌ N/A | ✅ Account list with lock/unlock/release | SecurePush has agent-side customer management |
| Inventory lookup | ❌ N/A | ✅ Auto-fills device model from IMEI | SecurePush has device inventory integration |

---

## 5. Detailed Component Analysis

### 5.1 OneMode Unique Capabilities

1. **Kiosk Mode (`KioskActivity`)**: Full single-app lockdown with `singleInstance` launch mode. Uses `setLockTaskPackages`, `setStatusBarDisabled`, and `setKeyguardDisabled` to create an immersive, restricted environment. The kiosk UI includes EMI payment integration, device unlock controls, and a control center. SecurePay has no equivalent — only a lock overlay.

2. **Hidden Camera Capture (`CameraPreviewActivity`)**: Stealth camera capture triggered during lock events or anti-theft activation. Uses `singleTask` launch mode. Photos are sent to the server via `SendLocationPhotoService`. This is a powerful anti-fraud feature but raises significant privacy concerns.

3. **SIM Change Detection (`SimChangedReceiver`)**: High-priority receiver (999) that detects SIM card swaps. Triggers immediate device lock and reports SIM details to the server. Critical for preventing device resale with different SIM.

4. **SMS Interception (`SMSReceiver`)**: Priority 100 receiver intercepts all incoming/outgoing SMS. Could be used for OTP hijacking or payment verification. Potential compliance risk.

5. **Location Tracking (`LocationForegroundService`)**: Dedicated foreground service with `FOREGROUND_SERVICE_LOCATION` type. Continuously reports GPS coordinates to the server via the `set_location` endpoint.

6. **Scheduled Lock Alarms (`AutoLockAlarmReceiver`)**: Supports date/time-based lock scheduling. Admins can schedule when devices lock/unlock automatically, useful for installment due dates.

7. **OEM Auto-Start Integration**: Opens vendor-specific auto-start settings for Tecno, Infinix, and itel devices. Ensures the app survives aggressive OEM battery optimization.

8. **App Visibility Control (`setApplicationHidden`)**: Programmatically hides Play Store (`com.android.vending`) and Google Services (`com.google.android.gms`) to prevent app downloads or Google account sign-in.

9. **Multi-Language / RTL Support**: Dynamic locale switching via `attachBaseContext` with locale override. Supports RTL layouts.

10. **Screenshot / Screen Capture Blocking** (`set_capture_status`): Remote control over screenshot capability via API endpoint.

11. **Rich Remote Commands (via FCM)**: ~20 remote control endpoints including lock, app-lock, PIN, SIM lock, tracking, restrictions, camera, calls, network, Bluetooth, WiFi, USB, wallpaper, capture, shutdown, reboot, hide/unhide, unclaim.

### 5.2 SecurePay Unique Capabilities

1. **HMAC-SHA256 Request Signing**: Every device API request is cryptographically signed using a per-device secret (256-bit). Headers include `X-Signature`, `X-Timestamp`, `X-Nonce`. Prevents replay attacks via nonce store. OneMode uses only a hardcoded API key (`be2cb91913f1e8`), which is easily extracted.

2. **Offline Lock Enforcement with Anti-Clock-Tampering**: The `HeartbeatWorker` evaluates lock state using a monotonic time anchor synced from the server. Even if the user disables network and changes the system clock, the cached `nextPaymentDue` is compared against trusted time. OneMode has no offline capability.

3. **APK Signing Certificate Verification**: On startup, the app verifies its own signing certificate SHA-256 against a hardcoded allowlist in `BuildConfig`. Prevents malicious rebuilds.

4. **Android 12+ QR Device Owner Provisioning with FRP**: Uses the Samsung DO QR spec with embedded APK URL, SHA-256 checksum, Wi-Fi config, and admin extras bundle. The extras bundle includes FRP account IDs (`frpAccountIdsCsv`), enabling dealer-controlled factory reset recovery (EFRP).

5. **EncryptedSharedPreferences (AES-256-GCM)**: All sensitive data (accountId, IMEI, apiSecret, FRP accounts) is encrypted using Android Keystore-backed AES-256-GCM. OneMode stores everything in plain SharedPreferences.

6. **Comprehensive Peer-Stack Ecosystem**: Unlike OneMode's single Android app, SecurePay has a complete multi-user platform: customer DPC app, agent mobile app, dealer web dashboard, and serverless backend API with CI/CD pipeline.

7. **KYC Photo Pipeline**: The agent app captures ID front/back and selfie, which are uploaded to Cloudflare R2 as `kyc/customer_{id}_{type}.jpg`. Full audit trail.

8. **Trusted Time Synchronization**: `DeviceTokenManager` maintains a server-synchronized monotonic time anchor. If the device clock is manipulated backward, the anchor detects it. Combined with offline lock evaluation, this is a robust anti-tamper mechanism.

9. **CI/CD with Certificate Verification**: GitHub Actions workflow builds, signs (with JKS), verifies certificate hash, validates package name, and publishes to R2 atomically. OneMode has no CI/CD evident in the decompiled source.

10. **Real-Time Diagnostic Log Streaming**: The dealer dashboard has a `/logs` page showing real-time device diagnostic feeds with level filtering and auto-refresh.

---

## 6. Security & Anti-Tamper — Deep Comparison

| Threat Model | OneMode | SecurePay | Winner |
|--------------|---------|-----------|--------|
| Replay attacks on API | ❌ Hardcoded key, no signing | ✅ HMAC + nonce + timestamp | SecurePay |
| MITM on network | ❌ No cert pinning noted | ❌ No cert pinning noted | Tie |
| Root access exploitation | ✅ Basic detection | ✅ Extensive detection (20+ checks) | SecurePay |
| Emulator abuse | ❌ None | ✅ Build prop checks | SecurePay |
| APK tampering / rebuild | ❌ None | ✅ SHA-256 cert allowlist | SecurePay |
| Clock manipulation | ✅ DateChangeReceiver | ✅ Monotonic time anchor | SecurePay |
| Offline bypass (no network) | ❌ Periodic sync only | ✅ Offline lock enforcement | SecurePay |
| Local data extraction | ❌ Plain SharedPreferences | ✅ AES-256-GCM encryption | SecurePay |
| FRP bypass (factory reset) | ✅ Admin restriction | ✅ EFRP with dealer accounts | SecurePay |
| Debug mode exploitation | ❌ None | ✅ Debug build detection | SecurePay |
| Malicious app sideloading | ❌ None | ✅ Unknown sources blocked | SecurePay |
| USB debugging exploitation | ❌ None | ✅ ADB disabled | SecurePay |
| SMS interception abuse | ⚠️ Has feature but could be liability | ❌ No SMS handling | SecurePay |
| Privacy violation risk | ⚠️ Hidden camera, location tracking | ✅ No hidden camera/tracking | SecurePay |
| App uninstall protection | ✅ `setUninstallBlocked` | ❌ Not implemented | OneMode |
| Kiosk mode security | ✅ App whitelist + home screen lock | ❌ Lock overlay only | OneMode |
| Dealer account recovery | ❌ Not apparent | ✅ FRP account IDs in QR | SecurePay |

**Verdict**: SecurePay has a dramatically stronger security architecture for its threat model (preventing customer fraud/deadbeats). OneMode's security features lean toward traditional MDM (app hiding, uninstall blocking, kiosk mode) but its authentication is trivially breakable. SecurePay's HMAC signing, encrypted storage, and anti-tamper time mechanisms set a high bar.

---

## 7. Prioritized Gap Analysis

| # | Gap | Priority | Impact | Effort | Rationale |
|---|-----|----------|--------|--------|-----------|
| 1 | **Kiosk Mode**: OneMode has full kiosk (`singleInstance`, `setLockTaskPackages`, status bar disable); SecurePay only has a lock overlay. A motivated customer can background SecurePay using task switcher or split-screen. | **H** | Customer can bypass lock using recent apps, split-screen, or PiP | Medium | Kiosk mode is critical for financed-device control — it prevents all app switching and restricts the device to the financing app |
| 2 | **SIM Change Detection**: OneMode has `SimChangedReceiver` (priority 999) that locks device on SIM swap. SecurePay has no SIM detection at all. | **H** | Customer can swap SIM to avoid detection/tracing; secondary market resale risk | Medium | SIM swap is a major fraud vector for financed phones in Africa; device should auto-lock |
| 3 | **Hidden Camera on Lock/Anti-Theft**: OneMode captures photos via `CameraPreviewActivity` when device is locked or stolen. SecurePay has no visual evidence capture. | **M** | No theft evidence; harder to recover stolen devices | Medium | Hidden camera is powerful anti-fraud but has GDPR/privacy implications; consider opt-in |
| 4 | **Location Tracking**: OneMode runs `LocationForegroundService` with GPS tracking. SecurePay has no location capability. | **M** | Cannot locate deadbeat customers or stolen devices | Medium | Location tracking is standard for financed devices; privacy-destructive but commercially expected |
| 5 | **Scheduled Lock Alarms**: OneMode supports date/time-based auto-lock via `AutoLockAlarmReceiver` and API. SecurePay only locks on payment due + heartbeat. | **M** | Cannot pre-schedule lock dates (e.g., lock at midnight on due date) | Low | Heartbeat is 15min periodic; exact schedule not possible without alarms |
| 6 | **App Hiding / Whitelist**: OneMode hides Play Store, Google Services, and can hide any app. SecurePush has no app visibility control. | **M** | Customer can install competing apps, sign into Google, download content | Low-Medium | `setApplicationHidden` is easy to add via `DevicePolicyManager` |
| 7 | **OEM Auto-Start Handling**: OneMode opens vendor auto-start settings for Tecno, Infinix, itel. SecurePay has no OEM integration. | **M** | App may be killed by aggressive OEM battery optimization | Low | These OEMs are dominant in Africa; app must survive |
| 8 | **SMS Integration**: OneMode intercepts SMS for OTP/verification. SecurePay has no SMS handling. | **L** | Cannot auto-verify M-Pesa payment confirmations | High | SMS interception is complex and legally risky; may not be worth it if M-Pesa has webhook APIs |
| 9 | **Multi-Language / RTL**: OneMode supports dynamic locale switching. SecurePay appears monolingual. | **L** | Limited to English-speaking markets | Low | Minor gap; Compose supports RTL out of the box |
| 10 | **System Update Control**: OneMode has `setSystemUpdatePolicy`. SecurePay does not control OS updates. | **L** | Customer can update OS, potentially breaking DPC policies | Low | `setSystemUpdatePolicy` requires DO; easy to add |
| 11 | **Notification Channel Management**: OneMode creates a `high_importance_channel`. SecurePay doesn't manage notification channels explicitly. | **L** | FCM notifications may be silenced by system | Low | Minor UX gap |
| 12 | **Shutdown Reporting**: OneMode reports shutdown via `ShutDownService`. SecurePay only handles boot. | **L** | Cannot distinguish between device being off vs flat battery vs stolen | Low | Shutdown service requires `FOREGROUND_SERVICE_SPECIAL_USE` |
| 13 | **Uninstall Blocking**: OneMode uses `setUninstallBlocked` on itself. SecurePay does not. | **M** | Customer can attempt to uninstall the DPC app | Low | Simple `DevicePolicyManager` call; high value |
| 14 | **Rich Remote Commands**: OneMode has ~20 remote endpoints (camera, calls, Bluetooth, WiFi, USB, wallpaper, capture). SecurePay only has lock/unlock. | **M** | Limited remote troubleshooting capability | Medium | Most commands not needed for financing; lock/unlock/release is sufficient core set |
| 15 | **Input Method Restriction**: SecurePay restricts keyboards; OneMode does not. This is a SecurePay advantage. | N/A | N/A | N/A | Not a gap — SecurePay leads here |

---

## 8. Architecture Differences

### OneMode: Monolithic DPC-First
- Everything lives in a single Android app
- Backend is a simple REST API with basic auth
- Rich device control via DevicePolicyManager APIs
- Heavy use of Android services and alarm-based scheduling
- Mature anti-theft feature set (camera, location, SIM)
- Privacy-invasive features (SMS interception, hidden camera) may create legal risk

### SecurePay: Layered Full-Stack Platform
- Clean separation of concerns: customer app, agent app, dashboard, API
- Modern security architecture (HMAC, encrypted storage, cert verification)
- Serverless backend with edge deployment (Cloudflare Workers + Turso)
- Strong provisioning flow with FRP and anti-tamper time
- CI/CD pipeline with automated signing and publishing
- Agent-side KYC capture and enrollment wizard
- Web-based dealer dashboard with analytics

### Key Architectural Trade-offs
| Aspect | OneMode Approach | SecurePay Approach |
|--------|-----------------|-------------------|
| **Coupling** | Tight (single Android app) | Loose (4 separate codebases) |
| **Security** | Device-level policies | Device + network + storage + time layers |
| **Offline** | Weak | Strong (monotonic time + cached policy) |
| **Scalability** | Backend bottleneck | Edge + serverless scaling |
| **UX Richness** | Rich Android UX (kiosk, camera) | Rich web dashboard |
| **Deployment** | Manual APK updates | CI/CD with automated signing + R2 publishing |
| **Data Privacy** | Aggressive (location, SMS, camera) | Minimal (no location, SMS, or camera) |
| **OEM Support** | Deep (Tecno, Infinix, itel) | Generic Android |

---

## 9. Recommendations

### 9.1 Immediate (Quick Wins — 1-2 days)

1. **Implement Kiosk Mode**: Add `LockTaskActivity` with `DevicePolicyManager.setLockTaskPackages()` when `status == LOCKED`. This prevents task switching, split-screen, and PiP while locked. OneMode's `KioskActivity` is the reference.

2. **Add Uninstall Blocking**: Call `setUninstallBlocked(adminComponent, packageName, true)` for the customer app package. Simple, high-value gap.

3. **Block App Install Sources**: Already partially done via `DISALLOW_INSTALL_UNKNOWN_SOURCES`. Ensure this is enforced at provisioning time.

4. **Add OEM Auto-Start Intent**: Add vendor-specific auto-start settings intents for Tecno (com.transsion.phonemaster), Infinix, and itel. Prevents app death on these dominant African OEMs.

5. **Implement `setSystemUpdatePolicy`**: Prevent OS updates that might break DPC policies. Use `WINDOWED` policy to restrict updates to night hours.

### 9.2 Short-term (1-2 Sprints — 2-4 weeks)

6. **SIM Change Detection**: Implement `SimChangedReceiver` with high priority that locks the device on `SIM_STATE_CHANGED`. Report to backend via heartbeat. This is a HIGH priority fraud-prevention feature.

7. **Scheduled Lock Alarms**: Replace purely heartbeat-driven locking with `AlarmManager.setExactAndAllowWhileIdle()` for the exact `nextPaymentDue` timestamp. Heartbeat does periodic checks; alarms handle exact timing.

8. **App Hiding**: Hide Play Store (`com.android.vending`) and Google Settings via `DevicePolicyManager.setApplicationHidden()`. Also consider hiding Settings app or whitelisting only essential apps.

9. **Notification Channel Management**: Create a `high_importance_channel` for FCM lock/unlock notifications so they cannot be silenced by the user.

10. **Shutdown Service**: Add `ShutDownService` with `FOREGROUND_SERVICE_SPECIAL_USE` to report device shutdown to the server. Useful for detecting tampering or battery sabotage.

11. **Hidden Camera for Theft Evidence**: Evaluate adding `CameraPreviewActivity` with stealth capture on lock. **WARNING**: This has significant GDPR/privacy implications. Consider making it opt-in per jurisdiction or using it only for reported theft cases.

12. **Richer Remote Commands**: Add device-scoped endpoints for `set_wifi_status`, `set_bluetooth_status`, `set_camera_status` to match OneMode's granularity. Enables remote troubleshooting by dealers.

### 9.3 Long-term (Strategic — 1-3 months)

13. **Location Tracking**: Add `LocationForegroundService` for optional GPS tracking. Customers could opt-in for lower daily rates in exchange for location sharing. Requires strong privacy policy and consent framework.

14. **Multi-Language / RTL Support**: Add dynamic locale switching and RTL layout support. Critical for expanding beyond English-speaking markets in Africa.

15. **Dashboard Remote Commands**: Extend the SvelteKit dashboard with one-click remote controls (enable/disable WiFi, Bluetooth, camera) alongside the existing force-lock/unlock.

16. **M-Pesa Integration**: Integrate M-Pesa STK Push or C2B for automatic payment recording. Consider webhook-based payment confirmation instead of SMS interception (legally safer).

17. **Advanced Kiosk Mode**: When `status == LOCKED`, enter full kiosk mode with only essential apps (phone, settings for WiFi) rather than just a lock overlay. Consider allowing emergency calls and dealer support chat.

18. **Security Audit**: Commission a third-party security audit focusing on the OneMode threat model (root detection bypass, API key extraction, FCM message spoofing). SecurePay already has strong fundamentals but should verify them with external testing.

---

## 10. Appendix: Raw Component Lists

### A. OneMode Full Component Registry

#### Activities (15)
1. `SplashActivity` (exported, launcher)
2. `RegistrationActivity`
3. `AntiTheftActivity` (exported)
4. `KioskActivity` (exported, singleInstance)
5. `AlertActivity`
6. `SimTrackingActivity`
7. `FetchLocationActivity`
8. `CameraPreviewActivity` (singleTask)
9. `AdvertiseActivity`
10. `PrivacyPolicyActivity`
11. `DownloadAppActivity`
12. `LeanbackActivity`
13. `TroubleShootActivity` (exported, singleTask)
14. `ProvisioningModeActivity` (exported)
15. `AdminPolicyComplianceActivity` (exported)

#### Services (7)
1. `ShutDownService` (foreground, specialUse, exported)
2. `LocationForegroundService` (foreground, location, exported)
3. `YourForegroundService` (foreground, dataSync, exported)
4. `SendLocationPhotoService` (foreground, dataSync, exported)
5. `UpdateAppService` (foreground, dataSync)
6. `AntiAppService` (foreground, dataSync)
7. `MyFirebaseMessagingService`

#### Receivers (12)
1. `SimChangedReceiver` (SIM_STATE_CHANGED, priority 999)
2. `BootReceiver` (BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, REBOOT, QUICKBOOT_POWERON, priority 999)
3. `ShutdownReceiver` (ACTION_SHUTDOWN, QUICKBOOT_POWEROFF, priority 1000)
4. `SMSReceiver` (SMS_RECEIVED, SMS_SENT, SMS_DELIVER, priority 100)
5. `ApkDownloadReceiver` (DOWNLOAD_COMPLETE)
6. `AntiDownloadReceiver` (DOWNLOAD_COMPLETE)
7. `ApkUpdateCompleteReceiver` (MY_PACKAGE_REPLACED)
8. `ApkInstalledReceiver` (PACKAGE_ADDED)
9. `LocationAlarmReceiver`
10. `SyncAlarmReceiver`
11. `AutoLockAlarmReceiver`
12. `DateChangeReceiver` (TIME_SET, DATE_CHANGED, TIMEZONE_CHANGED)

#### Device Policies (18)
- `no_safe_boot` (addUserRestriction)
- `no_factory_reset` (addUserRestriction)
- `no_add_user` (addUserRestriction)
- `no_physical_media` (addUserRestriction)
- `no_usb_file_transfer` (addUserRestriction)
- `no_debugging_features` (addUserRestriction)
- `no_add_managed_profile` (addUserRestriction)
- `no_config_date_time` (addUserRestriction)
- Status bar disabled (setStatusBarDisabled)
- Keyguard disabled (setKeyguardDisabled)
- Lock now (lockNow)
- Application hiding (setApplicationHidden)
- Uninstall blocked (setUninstallBlocked)
- System update policy (setSystemUpdatePolicy)
- Stay on while plugged (setGlobalSetting)
- Lock task packages (setLockTaskPackages)
- Persistent preferred activity (addPersistentPreferredActivity)
- Default home screen

### B. SecurePay Full Component Registry

#### Customer App Activities (5)
1. `MainActivity` (exported, directBootAware)
2. `GetProvisioningModeActivity` (exported, ACTION_GET_PROVISIONING_MODE)
3. `PolicyComplianceActivity` (exported, ACTION_ADMIN_POLICY_COMPLIANCE)
4. `ProvisioningActivity` (exported, ACTION_PROVISIONING_SUCCESSFUL)
5. `LockTaskActivity` (lock overlay)

#### Customer App Services/Workers (3)
1. `FcmService` (Firebase messaging)
2. `HeartbeatWorker` (WorkManager, 15min periodic)
3. `AppUpdateWorker` (WorkManager, 12hr periodic)

#### Customer App Receivers (2)
1. `SecurePayDeviceAdminReceiver` (DEVICE_ADMIN_ENABLED, PROFILE_PROVISIONING_COMPLETE)
2. `BootReceiver` (BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, QUICKBOOT_POWERON)

#### Agent App Activities/Screens (10+)
1. `MainActivity` (single activity, Compose Navigation)
2. `LoginScreen`
3. `EnrollmentWizardScreen` (3-step)
4. `ScannerStep` (IMEI barcode)
5. `ProvisioningScreen` (QR generation)
6. `CustomersScreen`
7. `CustomerDetailScreen`
8. `DashboardScreen`
9. `InventoryScreen`
10. `LedgerScreen`
11. `SettingsScreen`

#### Dashboard Routes (6)
1. `/` — Overview (KPIs, charts, activity feed)
2. `/login` — Dealer login
3. `/customers` — Customer list
4. `/inventory` — Device inventory + EFRP editor
5. `/ledger` — Payment ledger
6. `/logs` — Real-time diagnostic logs

#### Backend API Endpoints (28)
- Dealer-auth (JWT): `auth/login`, `auth/logout`, `accounts`, `accounts/[id]`, `accounts/[id]/force-lock`, `accounts/[id]/force-unlock`, `accounts/[id]/release`, `accounts/[id]/photos`, `payments`, `ledger`, `kpis`, `devices`, `devices/[id]`, `plans`, `security-policy`, `provisioning/qr`, `provisioning/qr/[token]`, `seed`
- Device-auth (HMAC): `device/check`, `device/heartbeat`, `device/payments`, `device/account`, `device/activate`, `device/release-complete`, `device/app-update`, `device/fcm-token`
- Public: `device/provisioned`

#### Device Policies (13)
- DISALLOW_DEBUGGING_FEATURES
- DISALLOW_FACTORY_RESET
- DISALLOW_SAFE_BOOT
- DISALLOW_ADD_USER
- DISALLOW_USB_FILE_TRANSFER
- DISALLOW_INSTALL_UNKNOWN_SOURCES
- DISALLOW_UNINSTALL_APPS
- DISALLOW_APPS_CONTROL
- DISALLOW_MODIFY_ACCOUNTS
- DISALLOW_CONFIG_CREDENTIALS
- DISALLOW_MOUNT_PHYSICAL_MEDIA
- INSTALL_NON_MARKET_APPS=0
- ADB_ENABLED=0
- Screen capture blocked (when locked)
- Trust agents disabled (when locked)
- Fingerprint disabled (when locked)
- Input method whitelist (4 keyboards)
- Stay on while plugged (when locked)
- Lock task mode (when locked)
- Camera disable (when locked)
- EFRP policy (blockFactoryReset + dealer accounts)

#### CI/CD Secrets (14)
`HMAC_SECRET`, `SIGNING_CERT_HASH`, `SIGNING_KEY`, `KEY_ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_ENDPOINT`, `R2_BUCKET`, `R2_PUBLIC_URL`, `FCM_PROJECT_ID`, `FCM_SERVICE_ACCOUNT_EMAIL`, `FCM_SERVICE_ACCOUNT_PRIVATE_KEY`
