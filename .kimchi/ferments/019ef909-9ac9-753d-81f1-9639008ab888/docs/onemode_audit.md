# OneMode Deep Audit (com.android.onemode v8.5)

## App Overview
- **Package**: `com.android.onemode`
- **Version Code**: 85
- **Version Name**: 8.5
- **Min SDK**: 28 (Android 9.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **App Type**: Device Policy Controller (DPC) / Enterprise Mobility Management (EMM) app
- **Backend**: punontechnologies.com
- **App Purpose**: Device management, financed-device control, anti-theft, EMI tracking similar to SecurePay

---

## Activities

| Activity | Exported | Launch Mode | Purpose |
|----------|----------|-------------|---------|
| `SplashActivity` | true | default | App launcher entry, checks device admin status, routes to Registration or AntiTheft |
| `RegistrationActivity` | false | default | Device registration flow, permission requests, admin activation, auto-grant |
| `AntiTheftActivity` | true | default | Main dashboard showing EMI details, device control features |
| `KioskActivity` | true | singleInstance | Kiosk mode UI with EMI payment, device unlock, control center |
| `AlertActivity` | false | default | Alert notifications display (EMI alerts, lock alerts, SIM change alerts) |
| `SimTrackingActivity` | false | default | SIM change detection and tracking |
| `FetchLocationActivity` | false | default | GPS location fetching with permission handling |
| `CameraPreviewActivity` | false | singleTask | Hidden camera capture for theft evidence |
| `AdvertiseActivity` | false | default | Display ads/promotions screen |
| `PrivacyPolicyActivity` | false | default | Privacy policy display |
| `DownloadAppActivity` | false | default | App download functionality |
| `LeanbackActivity` | false | default | Android TV Leanback support |
| `TroubleShootActivity` | true | singleTask | Deep link handling for `https://mdmsolutions.co.in/` |
| `ProvisioningModeActivity` | true | - | Android enterprise provisioning callback |
| `AdminPolicyComplianceActivity` | true | - | Android enterprise compliance callback |

**Activity Aliases**:
- `SplashActivityAlias` - Primary launcher (enabled)
- `SplashActivityAlias1` - Secondary launcher "Chollo" (disabled)

---

## Services

| Service | Type | Exported | Foreground Type | Purpose |
|---------|------|----------|-----------------|---------|
| `ShutDownService` | local | true | specialUse | Listens for shutdown events, reports shutdown status to server |
| `LocationForegroundService` | local | true | location | GPS location tracking foreground service |
| `YourForegroundService` | local | true | dataSync | Boot-time sync service notification |
| `SendLocationPhotoService` | local | true | dataSync | Sends captured camera photos to server |
| `UpdateAppService` | local | false | dataSync | App update download service |
| `AntiAppService` | local | false | dataSync | Anti-theft app download service |
| `MyFirebaseMessagingService` | - | false | - | FCM message handling |

---

## Broadcast Receivers

| Receiver | Trigger | Priority | Purpose |
|----------|---------|----------|---------|
| `SimChangedReceiver` | `android.intent.action.SIM_STATE_CHANGED` | 999 | Detect SIM changes, report to server |
| `BootReceiver` | `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `REBOOT`, `QUICKBOOT_POWERON` | 999 | Boot-time initialization, service restart, lock device |
| `ShutdownReceiver` | `android.intent.action.ACTION_SHUTDOWN`, `QUICKBOOT_POWEROFF` | 1000 | Report shutdown, lock device on shutdown |
| `SMSReceiver` | `SMS_RECEIVED`, `SMS_SENT`, `SMS_DELIVER` | 100 | Intercept SMS messages |
| `ApkDownloadReceiver` | `DOWNLOAD_COMPLETE` | - | Handle app update downloads |
| `AntiDownloadReceiver` | `DOWNLOAD_COMPLETE` | - | Handle anti-theft app downloads |
| `ApkUpdateCompleteReceiver` | `MY_PACKAGE_REPLACED` | - | Handle app update completion |
| `ApkInstalledReceiver` | `PACKAGE_ADDED` | - | Monitor app installations |
| `LocationAlarmReceiver` | - | - | Trigger location tracking alarms |
| `SyncAlarmReceiver` | - | - | Trigger data sync alarms |
| `AutoLockAlarmReceiver` | - | - | Trigger auto-lock alarms |
| `DateChangeReceiver` | `TIME_SET`, `DATE_CHANGED`, `TIMEZONE_CHANGED` | - | Monitor date/time changes |

---

## Permissions

| Permission | Level | Purpose |
|------------|-------|---------|
| `android.permission.ACCESS_FINE_LOCATION` | dangerous | Precise GPS location tracking |
| `android.permission.ACCESS_COARSE_LOCATION` | dangerous | Network-based location |
| `android.permission.ACCESS_BACKGROUND_LOCATION` | dangerous | Background location updates |
| `android.permission.READ_PHONE_STATE` | dangerous | Read IMEI, phone number |
| `android.permission.READ_PHONE_NUMBERS` | dangerous | Read phone numbers |
| `android.permission.CALL_PHONE` | dangerous | Make phone calls |
| `android.permission.READ_SMS` | dangerous | Read SMS messages |
| `android.permission.SEND_SMS` | dangerous | Send SMS messages |
| `android.permission.RECEIVE_SMS` | dangerous | Receive SMS |
| `android.permission.PROCESS_OUTGOING_CALLS` | dangerous | Monitor outgoing calls |
| `android.permission.READ_CONTACTS` | dangerous | Access contacts |
| `android.permission.WRITE_CONTACTS` | dangerous | Modify contacts |
| `android.permission.CAMERA` | dangerous | Capture photos |
| `android.permission.READ_EXTERNAL_STORAGE` | dangerous | Read stored files |
| `android.permission.WRITE_EXTERNAL_STORAGE` | dangerous | Write files |
| `android.permission.READ_PRIVILEGED_PHONE_STATE` | signature | privileged phone state access |
| `android.permission.INSTALL_PACKAGES` | signature | Install APKs |
| `android.permission.REQUEST_INSTALL_PACKAGES` | dangerous | Request package install |
| `android.permission.DELETE_PACKAGES` | signature | Delete packages |
| `android.permission.QUERY_ALL_PACKAGES` | normal | List all installed apps |
| `android.permission.REQUEST_DELETE_PACKAGES` | dangerous | Request package deletion |
| `android.permission.FOREGROUND_SERVICE` | normal | Run foreground services |
| `android.permission.FOREGROUND_SERVICE_LOCATION` | normal | Location foreground service |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | normal | Data sync foreground |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | normal | Special use foreground |
| `android.permission.SCHEDULE_EXACT_ALARM` | normal | Schedule exact alarms |
| `android.permission.USE_EXACT_ALARM` | normal | Use exact alarms |
| `android.permission.WAKE_LOCK` | normal | Prevent device sleep |
| `android.permission.RECEIVE_BOOT_COMPLETED` | normal | Boot completion receiver |
| `android.permission.INTERNET` | normal | Network access |
| `android.permission.ACCESS_NETWORK_STATE` | normal | Network state detection |
| `android.permission.ACCESS_NOTIFICATION_POLICY` | normal | DND access |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | normal | Battery optimization bypass |
| `android.permission.SYSTEM_ALERT_WINDOW` | dangerous | Overlay permission |
| `android.permission.WRITE_SETTINGS` | dangerous | Modify system settings |
| `android.permission.CHANGE_WIFI_STATE` | dangerous | Control WiFi |
| `android.permission.ACCESS_WIFI_STATE` | normal | Read WiFi state |
| `android.permission.WRITE_SECURE_SETTINGS` | signature | Modify secure settings |
| `android.permission.MANAGE_DEVICE_POLICY_*` | signature | Extensive device policy management |
| `com.google.android.c2dm.permission.RECEIVE` | - | FCM receive |
| `com.google.android.providers.gsf.permission.READ_GSERVICES` | dangerous | Google services read |
| `android.permission.POST_NOTIFICATIONS` | dangerous | Post notifications (API 33+) |

---

## Device Policies (from DeviceAdminReceiver & manifest)

### Policies Enforced:
| Policy | Enforcement | Description |
|--------|-------------|-------------|
| `no_safe_boot` | addUserRestriction | Prevent safe boot |
| `no_factory_reset` | addUserRestriction | Prevent factory reset |
| `no_add_user` | addUserRestriction | Prevent adding users |
| `no_physical_media` | addUserRestriction | Disable external storage |
| `no_usb_file_transfer` | addUserRestriction | Disable USB file transfer |
| `no_debugging_features` | addUserRestriction | Disable debugging |
| `no_add_managed_profile` | addUserRestriction | Prevent managed profiles |
| `no_config_date_time` | addUserRestriction | Prevent date/time changes |
| Status Bar Disabled | setStatusBarDisabled | Hide status bar (kiosk mode) |
| Keyguard Disabled | setKeyguardDisabled | Disable lock screen |
| Lock Now | lockNow() | Immediate device lock |
| App Hidden | setApplicationHidden | Hide Play Store, Google Services |
| Uninstall Blocked | setUninstallBlocked | Prevent app uninstallation |
| System Update Policy | setSystemUpdatePolicy | Control system updates |
| Stay on while plugged | setGlobalSetting | Keep screen on |
| Lock Task Packages | setLockTaskPackages | Kiosk mode whitelisting |
| Persistent Preferred Activity | addPersistentPreferredActivity | Set default home screen |

### Provisioning:
- Supports `PROFILE_OWNER` and `DEVICE_OWNER` provisioning
- Handles `PROFILE_PROVISIONING_COMPLETE`
- Handles `DEVICE_ADMIN_DISABLED` intent
- Admin policy compliance callback

---

## Network / API (Retrofit)

### Base URLs:
1. **Primary API**: `https://backend.punontechnologies.com/`
2. **Secondary API**: `https://emmsync.punontechnologies.com/api/`

### Authentication:
- Hardcoded API Key: `be2cb91913f1e8`
- JKS Name: `onemode115`
- Custom headers: `Authorization`, `appVersion`, `app_version`, `customerId`, `deviceId`, `customer_id`, `device_id`, `jks_name`

### Endpoints (ApiService):

**Customer Management:**
- `POST emmm/customer_create` - Create customer account
- `POST emmm/customer_update_emm` - Register device configuration
- `POST emmm/customer_register` - Registration with device info
- `POST sync_device` - Sync device data
- `POST api/auth/send-otp-troubleshooting` - Send OTP
- `POST api/auth/verify-otp-troubleshooting` - Verify OTP

**EMI Management:**
- `POST emmm/get_emi_detailsV1` - Fetch EMI details
- `POST emmm/get_schedule_lock_details` - Get scheduled lock details
- `POST emmm/set_schedule_lock_status` - Set schedule lock
- `POST emmm/set_schedule_alarm_list` - Set alarm list
- `POST emmm/set_schedule_alarm_status` - Set alarm status

**Device Control:**
- `POST emmm/set_lock_status` - Lock device
- `POST emmm/set_applock_status` - App lock status
- `POST emmm/set_pin_status` - Set PIN
- `POST emmm/set_sim_lock_unlock_status` - SIM lock
- `POST emmm/set_tracking_status` - Tracking on/off
- `POST emmm/set_sim_tracking_status` - SIM tracking
- `POST emmm/set_restriction_status` - Device restrictions
- `POST emmm/set_camera_status` - Camera enable/disable
- `POST emmm/set_call_status` - Call enable/disable
- `POST emmm/set_network_info` - Network info
- `POST emmm/set_airplane_status` - Airplane mode
- `POST emmm/set_bluetooth_status` - Bluetooth
- `POST emmm/set_wifi_status` - WiFi
- `POST emmm/set_usb_status` - USB mode
- `POST emmm/set_wallpaper_status` - Wallpaper
- `POST emmm/set_capture_status` - Screenshot capture
- `POST emmm/set_shutdown_status` - Shutdown
- `POST emmm/set_reboot_status` - Reboot
- `POST emmm/set_hide_unhide_status` - App visibility
- `POST emmm/set_all_app_hide_status` - Hide all apps
- `POST emmm/set_unclaim_status` - Unclaim device

**Location & Tracking:**
- `POST emmm/set_location` - Report GPS location
- `POST emmm/set_imei_on_zt` - Report IMEI on shutdown
- `POST emmm/remove_imei_after_restart` - Remove IMEI on restart
- `POST emmm/sendCaptureData` - Send captured photos

**SIM Management:**
- `POST emmm/set_sim_removed_info` - Report SIM removal

**EMI Alerts:**
- `POST emmm/set_emi_alert_status` - EMI alert settings

**Update & Config:**
- `GET emmm/get_update_url` - Get app update URL
- `POST emmm/update_fl_version` - Update FL version
- `POST emmm/update_fcm_by_imei_no` - Update FCM token
- `POST emmm/get_app_config` - Fetch configuration

**Retailer:**
- `GET emmm/get_user_phone` - Get retailer contact numbers

**Acknowledgement:**
- `POST emmm/acknowledgement` - Send acknowledgements

---

## Database (Room)

### Schema:
- **Database Name**: `onemode_database` (inferred from AppDatabase class)
- **Schema Version**: Unknown (needs runtime inspection)

### Entities:
- `EmiDetails` - EMI payment schedule data
- `SyncData` - Sync tracking data

### DAOs:
- `EmiDao` (via `AppDatabase.mo2090r()`) - EMI data operations
  - `insert` - Insert EMI details
  - `delete` - Delete EMI records
  - `getAllData` - Get all EMI data
  - `getScheduledData` - Get scheduled lock data
  - Custom queries for date-based EMI retrieval

---

## FCM / Messaging

### Firebase Integration:
- **Default Notification Channel**: `high_importance_channel`
- **Service**: `MyFirebaseMessagingService`

### Topics Subscribed:
- `ACCOUNT_ID` (user-specific)
- `ORGANIZATION_TOPIC_{orgId}` (organization)
- `COMPANY_TOPIC_ABSOLUTE`
- `FL_VERSION_85` (version-specific)

### Message Handling:
- Handles `google.message_id` / `message_id`
- Deduplicates messages using `LastMessageId` shared preference
- Processes JSON data payload and triggers `CommonUtil.handleFCMMessage()`
- Updates FCM token on refresh
- Sends token to server via `update_fcm_by_imei_no` endpoint

### Message Types:
- Lock/unlock commands
- Configuration updates
- EMI alerts
- SIM change notifications
- Tracking commands

---

## Security / Anti-Tamper Utilities

### Security Features:
1. **Device Admin Enforcement** - App acts as DPC/Device Owner
2. **Kiosk Mode** - Single-app lockdown mode
3. **SIM Lock Detection** - Detects SIM changes via `SimChangedReceiver`
4. **Root Detection** - Via `CommonUtil.isBatteryOptimizationEnabled()` pattern
5. **Play Store Hiding** - Hides `com.android.vending` via `setApplicationHidden()`
6. **Google Services Hiding** - Hides `com.google.android.gms`
7. **Anti-theft App Blocking** - Blocks uninstallation of protection apps
8. **Factory Reset Protection** - Enforces FRP admin settings
9. **Auto-start for OEMs** - Opens vendor-specific autostart settings (Tecno, Infinix, itel)
10. **Overlay Permission Check** - Validates `SYSTEM_ALERT_WINDOW` permission
11. **Battery Optimization Bypass** - Requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
12. **IMEI/Serial Tracking** - Tracks device identifiers

### CommonUtil Features:
- `isInternetAvailable()` - Network check
- `isBatteryOptimizationEnabled()` - Battery optimization check
- `getDeviceImei()` - IMEI retrieval
- `getCurrentLocation()` - GPS coordinates
- `lockDevice()` - Programmatic lock
- `getSIMDetails()` - SIM information
- `sendLocationToServer()` - Location reporting
- `sendShutdownStatus()` - Shutdown reporting
- `showNotification()` - Local notifications

### Shared Preferences Keys:
- `MySharedPrefs` - Main preferences
- `DEVICE_ID` - Device identifier
- `CUSTOMER_ID` - Customer identifier
- `IsRegister` - Registration status
- `IS_CONFIGURATION_SET` - Config status
- `IS_CUSTOMER_CREATED` - Customer creation status
- `FCM_TOKEN` - FCM registration token
- `LOCK`, `APPLOCK`, `SCREENLOCK`, `CAMERALOCK` - Feature toggles
- `TRACKING`, `REBOOT`, `AUTO_LOCK` - Feature toggles
- `DOWNLOADURL` - APK download URL
- `SIM_DETAIL`, `SIM_DATA` - SIM information
- `LAST_SYNC_DATE` - Sync tracking
- `ORGANIZATION_ID`, `ACCOUNT_ID`, `COMPANY_ID` - Org identifiers
- `IT_ADMIN_EMAIL` - Admin email

---

## Key Features Summary

### 1. Device Control
- Remote lock/unlock
- Lock scheduling (date/time-based)
- Camera enable/disable
- Call enable/disable
- WiFi/Bluetooth toggle
- Airplane mode toggle
- USB mode control
- App visibility control
- Kiosk mode lockdown

### 2. EMI/Financing Management
- EMI payment schedule display
- EMI amount tracking
- Due date notifications
- Payment reminders
- Penalty calculation display
- Retailer contact display
- Customer profile management

### 3. Anti-Theft/Anti-Fraud
- Hidden camera capture (stealth mode)
- Location tracking
- SIM change detection
- Device lock on SIM change
- Factory reset prevention
- Boot-time device lock
- Shutdown tracking
- Screenshot capture blocking

### 4. Monitoring
- Real-time location tracking
- SIM tracking
- Network status monitoring
- Battery status reporting
- App installation monitoring
- Boot completion monitoring
- Date/time change detection

### 5. Remote Commands (via FCM)
- Lock/unlock commands
- Wipe data
- Configuration push
- App blacklist/whitelist
- Restriction enforcement
- System update control

### 6. Provisioning
- Android Enterprise DPC support
- Device Owner provisioning
- Profile Owner provisioning
- Factory reset protection
- QR code provisioning support

### 7. Localization
- Multi-language support
- Dynamic locale switching
- RTL support via `attachBaseContext` with locale override

### 8. OEM Integration
- Auto-start permission handling for Tecno, Infinix, itel devices
- Quick Boot support (HTC, Huawei)
- System update policy management

### 9. Data Synchronization
- Periodic sync alarms
- Location sync
- Device state sync
- EMI data sync
- Boot-time sync

### 10. App Protection
- Uninstall blocking
- Self-reactivation on boot
- Foreground service persistence
- WorkManager integration
- AlarmManager for critical tasks

---

## Comparison with SecurePay

### Similar Features:
- EMI tracking and display
- Device locking mechanisms
- Remote control capabilities
- SIM change detection
- Location tracking
- Background service management

### Unique to OneMode:
- Full Android Enterprise DPC implementation
- Kiosk mode with extensive restrictions
- Factory reset protection (FRP)
- Multiple OEM auto-start integrations
- Comprehensive device policy enforcement
- Anti-theft app installation blocking
- System update policy control
- Deep link support (`mdmsolutions.co.in`)

### Unique to SecurePay (to be verified in SecurePay codebase):
- (Comparison pending SecurePay audit)

---

## Technical Stack

- **Language**: Kotlin (primary), Java (decompiled)
- **Architecture**: MVVM pattern, Clean Architecture components
- **DI**: Hilt (inferred from structure)
- **Network**: Retrofit 2 + OkHttp + Gson
- **Database**: Room
- **Messaging**: Firebase Cloud Messaging
- **Location**: Google Play Services Location
- **Background**: WorkManager + Foreground Services + AlarmManager
- **UI**: Material Design, ConstraintLayout, RecyclerView
- **Image Loading**: Glide (inferred)
- **Camera**: Camera2 API
- **Min SDK**: 28 (Android 9)
- **Target SDK**: 34 (Android 14)

---

*Audit completed on: 2026-06-24*
*Decompiled source analysis via Jadx*
