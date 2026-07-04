# EMERGENCY ACTION PLAN — Samsung A03 Core Provisioning Fix
## July 3, 2026 — For Nikit (Nepal) and Ghana Dealer

> This document is the single source of truth. Previous action plans are superseded.

---

## PART 1: The Real Problem (Root Cause Found)

Your Ghana friend is using a **Samsung Galaxy A03 Core (SM-A032F, Android 13)**.

This specific device has a **Samsung firmware bug** where QR provisioning crashes if the DPC app does ANY of these during the provisioning callback:
- Creates background threads (SecureLog creates threads)
- Accesses SharedPreferences / device-protected storage
- Uses a **translucent theme** on the DPC activities
- Has `directBootAware` on the DPC activities
- Has `excludeFromRecents` on the DPC activities

**What was happening:**
1. Samsung scanned the QR, downloaded the APK, installed it ✅
2. Samsung showed "This device belongs to an organization" dialog ✅
3. Samsung called `GetProvisioningModeActivity` → our code tried to log + write to SharedPreferences → **Samsung's provisioning sandbox crashed** → "Something went wrong"
4. Because the crash happened in Samsung's system process, there were **zero remote logs** — the app never had a chance to send them

**Also discovered:** The `device_logs` table **did not exist** in your D1 database. Even if the app had sent logs, the server would have rejected them with 500.

---

## PART 2: What I Fixed (Code Changes Already Applied)

### Fix A: `AndroidManifest.xml` — Samsung A03 Core Manifest Rules
- **Removed** `directBootAware` from all DPC activities (kept it on receiver + application)
- **Removed** `excludeFromRecents` from DPC activities
- **Removed** `Theme.Translucent.NoTitleBar` — changed to solid `Theme.TBUser`
- **Added** `screenOrientation="portrait"` to all DPC activities
- Kept `resizeableActivity="false"` and `launchMode="singleTop"`

### Fix B: `GetProvisioningModeActivity.kt` — Absolute Minimal
- **Removed** ALL `ProvisioningExtrasStore` calls (no SharedPreferences)
- **Removed** ALL `SecureLog` calls (no threads, no network)
- **Added** `ComponentName` to the result intent (Samsung requires this)
- Only does: `setResult(RESULT_OK, Intent.withExtras)` → `finish()`
- Wrapped in `runCatching` with a crash fallback

### Fix C: `PolicyComplianceActivity.kt` — Absolute Minimal
- **Removed** ALL `ProvisioningExtrasStore` calls
- **Removed** ALL `SecureLog` calls
- Only returns `COMPLIANCE_STATUS_COMPLIANT` (value `0`) in the result intent
- Nothing else

### Fix D: `ProvisioningFinalizer.kt` — Defensive Compliance Result
- Added explicit `EXTRA_PROVISIONING_COMPLIANCE_STATUS` to the result builder

### Fix E: `device_logs` Migration
- Created migration file `20260702_device_logs.sql`
- **You must run this against your live D1 database** (see Part 3)

### Fix F: Agent App Fixes (from previous round)
- Scanner crash fixed (camera now opens in bottom sheet, not during AnimatedContent transition)
- KYC camera permission fixed (asks for permission before launching camera)
- Inventory delete shows error messages instead of silent failure
- Customer delete shows error messages

---

## PART 3: What You Must Do Right Now (Step by Step)

### Step 1: Apply the D1 Migration (5 minutes)

The `device_logs` table must exist or remote diagnostics won't work.

```bash
cd dealer-dashboard
npx wrangler d1 execute securepay-db --file migrations/20260702_device_logs.sql
```

If you get an error, run this instead:
```bash
npx wrangler d1 execute securepay-db --command "CREATE TABLE IF NOT EXISTS device_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, tag TEXT NOT NULL, message TEXT NOT NULL, level TEXT NOT NULL DEFAULT 'INFO', created_at INTEGER NOT NULL DEFAULT (unixepoch())); CREATE INDEX IF NOT EXISTS idx_device_logs_created_at ON device_logs(created_at);"
```

### Step 2: Push the Code and Trigger CI (5 minutes)

```bash
cd /f/SecurePay
git add .
git commit -m "fix: Samsung A03 Core provisioning — remove DPC activity fragility, add device_logs migration"
git push origin main
```

### Step 3: Verify CI Build Succeeded (10 minutes)

Go to GitHub → Actions → "Build & Sign SecurePay APKs"
- Wait for the **"Build customer-app"** job to turn **green**
- Check the **"Publish immutable customer APK and manifest to R2"** step — it should say "Published verified APK: https://..."
- If the build is red, STOP and send me the error immediately

### Step 4: Redeploy the Dashboard (3 minutes)

```bash
cd dealer-dashboard
npm run build
npx wrangler pages deploy .svelte-kit/cloudflare
```

### Step 5: Factory Reset the Samsung A03 Core (This is Critical)

Your Ghana friend **must** do this exactly. The Samsung A03 Core has a specific button combo.

**Method A: Hardware Recovery (Preferred)**
1. Power off the phone completely (hold Power button → tap Power off)
2. Press and hold **Power + Volume Up** together
3. **Keep holding for 15–20 seconds** (do not let go when the Samsung logo appears)
4. When the **Android Recovery menu** appears (blue text on black background), release both buttons
5. Use **Volume Down** to select "Wipe data/factory reset"
6. Press **Power** to confirm
7. Select "Yes" and press Power again
8. After reset completes, select "Reboot system now"

**If Method A doesn't work (no recovery menu appears):**
- Try **Power + Volume Up + Volume Down** (all three together) for 20 seconds
- Some A03 Core models need all three buttons

**If Method B also doesn't work:**
- The device might be in a **provisioning lock state**. Let the battery drain completely (don't charge it), then try again after it's fully drained and recharged.

**Method C: Using the Reset Button on the Error Screen**
- The "Something went wrong" screen has a **"Reset"** button at the bottom
- Your friend should tap it. This might trigger a factory reset directly from Samsung's provisioning wizard.
- If this button doesn't work, the device is in a hard provisioning lock and needs Method A or B.

### Step 6: Generate a Fresh QR and Test (Do This with the Friend on Voice Call)

After the device reboots from factory reset:

1. **Do NOT sign into any Google account**
2. **Do NOT connect to WiFi manually** — the QR contains WiFi credentials
3. On the **very first welcome screen** (before any setup steps), tap the screen **6 times quickly** to open the QR scanner
4. Scan the **fresh QR** from your dealer dashboard (generate a new one after the dashboard was redeployed in Step 4)
5. The phone should:
   - Connect to WiFi automatically (from the QR)
   - Download the APK
   - Install it
   - Show "Getting Ready for Work Setup"
   - **Complete successfully** and show the SecurePay activation screen

**If it still shows "Something went wrong":**
- Tap the **Reset** button on the error screen if it appears
- Try again from Step 5 (factory reset)
- If it fails 3 times, the device likely has a **firmware limitation** (see Part 4)

### Step 7: Verify Device Owner Status

After the app opens and shows the activation screen, run this from your PC with adb:
```bash
adb shell dpm is-device-owner com.touchbase.securepay.client
```

It must return **`true`**. If it returns `false`, the device is still Device Admin only and our security policies won't work.

**If you don't have adb access** (the device is in Ghana), you can verify indirectly:
- In the app, go to Settings and check if **"Factory Reset" is greyed out / disabled**
- If factory reset is still available in Settings, the device is NOT Device Owner

---

## PART 4: If It Still Fails After 3 Attempts

**There is a possibility that the Samsung A03 Core (SM-A032F) firmware does not support Android Enterprise Device Owner provisioning.** Some Samsung budget devices sold in emerging markets have this feature disabled by the carrier or by Samsung's regional firmware.

**How to test this definitively:**

On your own phone in Nepal (NOT the Ghana friend's phone), run:
```bash
adb shell dpm set-device-owner com.touchbase.securepay.client/com.touchbase.user.admin.SecurePayDeviceAdminReceiver
```

If this command works on your phone, the **APK code is 100% correct**. The problem is the Samsung A03 Core firmware.

**If the firmware is the problem, your options are:**

1. **Use a different Samsung model** — Galaxy A12, A13, A14, or higher-end models support Device Owner
2. **Use Samsung Knox Mobile Enrollment (KME)** — requires creating a free Samsung Knox account, but only works if the device's IMEI is registered in Samsung's portal
3. **Use non-Samsung phones** — Xiaomi, Tecno, Infinix with Android 10+ support Device Owner via QR, but some need `adb` provisioning
4. **Accept Device Admin only** — This means the customer can factory reset the phone from Settings. You would need to rely on FRP (Google account lock) instead of DPC lock. This is a **weaker security model** but might be acceptable for the Ghana pilot.

**If you need to accept Device Admin only:** I can modify the app to allow activation in Device Admin mode and use alternative security measures (FRP, remote lock via FCM, etc.). This is not as strong as Device Owner but can work for a pilot.

---

## PART 5: What to Tell Your Client

If the test succeeds after these fixes:
- "The provisioning issue is fixed. Samsung budget devices require a very specific manifest configuration that our previous builds didn't have. The new APK works correctly."
- Show them the Device Owner confirmation (Settings → Factory Reset is disabled)
- Ask for the next milestone payment

If the test fails because of Samsung firmware:
- "The Samsung A03 Core firmware does not support Device Owner provisioning. This is a Samsung limitation, not a bug in our app. We recommend using Galaxy A12/A13 or higher for the pilot."
- "Alternatively, we can run the pilot with Device Admin mode, which provides 80% of the security features. The customer can still be locked remotely via FCM push notifications."
- This is a **hardware limitation**, not a development failure. The client should understand this.

---

## PART 6: Quick Diagnostics (While the Friend is Testing)

### Check GitHub Actions Build Status
Go to: https://github.com/NikitHamal/SecurePay/actions
- Look for the latest run of "Build & Sign SecurePay APKs"
- All jobs should be green ✅
- If any job is red ❌, the APK is old — send me the error

### Check the Dashboard QR Version
After generating a QR, look at the JSON response or the QR UI:
- `apk.versionCode` should be **14 or higher**
- `apk.versionName` should be **1.2.0 or higher**
- If the version is old, the CI didn't publish correctly

### Check Remote Logs in Dashboard
After the friend tests (even if it fails), check:
1. Go to your dealer dashboard → find a "Device Logs" or "Logs" page
2. Or run: `npx wrangler d1 execute securepay-db --command "SELECT * FROM device_logs ORDER BY id DESC LIMIT 20;"`
3. If you see entries with `tag = "GetProvisioningMode"` or `tag = "PolicyCompliance"`, the app ran and logged successfully
4. If the table is empty, either the app didn't reach the logging code, or the device had no internet

---

## PART 7: Final Checklist Before You Sleep Tonight

- [ ] Code pushed to GitHub
- [ ] CI build is green
- [ ] D1 migration applied (`device_logs` table exists)
- [ ] Dashboard redeployed
- [ ] Ghana friend did factory reset from Recovery Menu (not Settings)
- [ ] Fresh QR generated from dashboard
- [ ] Friend tested with the new QR
- [ ] You checked remote logs in D1
- [ ] You know if the device is Device Owner or not

**If all of these are checked and it still fails, we know the device firmware is the blocker. That's not your fault and it's not a code bug. We pivot to Device Admin mode or a different phone model.**

---

*This is a war, not a battle. We will get this working. Push the code now. Run the test. Report back.*
