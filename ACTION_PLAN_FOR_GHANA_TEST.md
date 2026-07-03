# Immediate Action Plan — Samsung A07 Provisioning Fix (Corrected July 2, 2026)

> **READ FIRST:** AI agents and future assistants must read `AI_AGENTS_NOTES.md` before giving any advice to this user. Do NOT suggest `adb`, TeamViewer, disabling Play Protect from the welcome screen, or any other impractical steps.

---

## What Was Fixed in Code (Already Pushed)

I applied these fixes to the repo. You only need to push, build, and test.

| File | Fix |
|------|-----|
| `PolicyComplianceActivity.kt` | **Removed ALL DPM calls.** Samsung Knox aborts provisioning if DevicePolicyManager is touched during `ADMIN_POLICY_COMPLIANCE`. Now it only records stage + persists extras, then returns `RESULT_OK` immediately. |
| `ProvisioningFinalizer.kt` | Added `allowDpmCalls` parameter so callers can safely skip DPM checks during the compliance callback. |
| `AndroidManifest.xml` | Added `resizeableActivity="false"` + `launchMode="singleTop"` to DPC activities. Added `ACTION_PROVISIONING_COMPLETE` to the receiver. |
| `dealer-dashboard/src/lib/api/server.ts` | QR payload now includes `PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM` from `latest.json`. Samsung requires this to trust the downloaded APK. |
| `SecurePayApplication.kt` | Removed hardcoded Firebase app-ID hash. Now loads `FCM_APPLICATION_ID` from `BuildConfig`. |
| `device_admin.xml` | Removed deprecated `<watch-login>` and `<encrypted-storage>` policies. |
| `build.gradle.kts` (customer) | Added `FCM_APPLICATION_ID` BuildConfig field. |
| CI workflows | Added `FCM_APPLICATION_ID` secret passing + Node.js 20 deprecation bridge. |
| `SecureLog.kt` | Fixed remote logging: non-daemon threads for force logs, longer timeouts, 1 retry for critical provisioning errors. |

---

## Step 1 — Push & Build

```bash
git add .
git commit -m "fix: Samsung A07 provisioning — remove DPM from compliance, add signature checksum, manifest attrs, fix remote logging"
git push origin main
```

GitHub Actions will run `build-apks.yml` automatically. Wait for the **"Build customer-app"** job to finish and verify the APK is uploaded to R2 (`latest.json` is updated).

### Add New GitHub Secret (Optional — Only If You Want FCM Push)
In your repo **Settings → Secrets and variables → Actions**, add:
- **Name:** `FCM_APPLICATION_ID`
- **Value:** Your Firebase mobile app ID (the full `1:...:android:...` string from Firebase Console). If you don't have it yet, leave it blank — Firebase will be skipped and the app still works.

---

## Step 2 — Deploy Dashboard

The dealer dashboard must be redeployed so the new `buildQrPayload()` code is live:
```bash
cd dealer-dashboard
npm run build
npx wrangler pages deploy .svelte-kit/cloudflare
```

---

## Step 3 — The Real Play Protect Problem (Read This Carefully)

**You cannot disable Play Protect from the welcome screen.** There is no Settings app, no Google menu, and no toggle during OOBE. Any advice telling you to do so is wrong.

Play Protect is currently blocking the APK installation because:
- The APK is downloaded from an unknown R2 URL (not Google Play Store).
- The app requests Device Owner / Device Admin privileges.
- The signing certificate is not in Google's trusted database yet.

**This is the ONLY reliable way to stop Play Protect from blocking:**

### Path A — Google Play Store (The Real Fix)
1. Wait for the client's **new Google Play Console account** to be verified.
2. Upload the signed APK (from the CI artifact) to **Internal Testing** in Play Console.
3. Google indexes the APK signature within 24–48 hours.
4. Once indexed, Play Protect will stop flagging the APK **even if the QR download still comes from your R2 bucket**.
5. This is the standard path used by M-KOPA and all other DPC providers.

### Path B — Test with a Pre-Trusted Build (Temporary)
If you have an older APK that was NOT flagged by Play Protect (e.g., the version that successfully gave "Admin only" before), you can temporarily test the provisioning logic with that APK. But it won't have the new code fixes, so this is only useful to confirm the dashboard QR generation works.

### Path C — Use a Google Play Store URL in the QR (Requires Path A First)
Once the app is on Play Store, the QR payload can be changed to point to the Play Store URL instead of the R2 URL. The Setup Wizard then installs the app from Play Store, which Play Protect never blocks. This is a future optimization.

---

## Step 4 — What to Test Right Now (With Play Protect Still Active)

Even though Play Protect may block the APK, you should still test because:
- **Android Enterprise system-level provisioning MIGHT bypass Play Protect.** The Setup Wizard is a system app with special privileges. Some devices allow system installations even when Play Protect would block a user-initiated install.
- You need to know **exactly where it fails** to report to the client. The remote logs will tell you.

### Test Procedure for Ghana Friend
1. **Factory reset** the Samsung A07 from **recovery menu** (power + volume up + home).
2. Go through the welcome screen until you reach **Wi-Fi setup**. Connect to Wi-Fi.
3. On the welcome screen, tap **6 times** to open QR scanner.
4. Scan the **fresh QR** generated from the dealer dashboard (must be generated AFTER dashboard was redeployed in Step 2).
5. Wait. Watch what happens:
   - If it says **"Setting up for work…"** and then finishes → provisioning worked.
   - If it says **"Something went wrong"** or **"Play Protect blocked this app"** → the code fixes are correct but Play Protect is the blocker.
6. **Open the dealer dashboard** → go to **Device Logs** (`/api/device/logs` or check the D1 table `device_logs`). Look for entries from the Samsung A07.
   - If you see logs like `GET_PROVISIONING_MODE_INVOKED`, `ADMIN_POLICY_COMPLIANCE`, `PROVISIONING_SUCCESSFUL` → the code worked, but Play Protect or Samsung Knox blocked the final step.
   - If you see NO logs at all → the app crashed before it could send a log, or the device had no internet during the callback.

### What to Tell the Client After This Test
- If the app provisions but Play Protect blocks it → show the client the logs proving the app is functional, and tell them the ONLY remaining step is Google Play Console verification.
- If the app still shows "Something went wrong" with no logs → we need another round of fixes.

---

## Step 5 — Long-Term: Non-Samsung Phones (Tecno, Infinix, etc.)

**Samsung Knox Mobile Enrollment (KME) is free, but it only works on Samsung phones.** The dealer also sells Tecno and other brands. **QR-based Android Enterprise provisioning is the ONLY path for non-Samsung phones.** Therefore, you MUST make the QR flow robust regardless of KME.

For non-Samsung phones:
- QR provisioning is supported on Android 8+ (API 26+) via the standard Setup Wizard.
- Some brands (e.g., Xiaomi) may require enabling a special setting before QR works.
- Tecno/HiOS/XOS sometimes use a custom Setup Wizard that does not support QR provisioning at all. You may need to use `adb shell dpm set-device-owner` for those devices, which requires the dealer or a technician to do it in-store before handing the phone to the customer.

**Recommendation:** Start with Samsung phones only for the pilot. Once the client is happy and paying, expand to Tecno using the QR flow if supported, or using in-store `adb` provisioning by a technician.

---

## Step 6 — Remote Logs Are Your Only Diagnostic Tool

**Do not suggest `adb logcat` to the user.** The Ghana client is a non-technical dealer. The user in Nepal cannot physically access the phone. The ONLY way to see what happened is via the remote log table in the dashboard (`device_logs`).

The `SecureLog` endpoint (`/api/device/logs`) is intentionally open (no HMAC required) so that even unactivated devices can send provisioning diagnostics. After every test, the user should check the dashboard logs for entries from `SecurePayDPC`, `ProvisioningExtras`, `MainActivity`, etc.

---

## Summary of What to Do Right Now

1. **Push the code fixes** (Step 1).
2. **Redeploy the dashboard** (Step 2).
3. **Generate a fresh QR** and test on the Samsung A07.
4. **Check the remote logs** in the dashboard immediately after the test.
5. **Report the result to me:**
   - What did the screen show? ("Setting up for work…" / "Something went wrong" / "Play Protect blocked" / other)
   - What logs appeared in the dashboard?
6. **If it fails**, send me the exact screen text and the last 20 log entries. I will diagnose within minutes.
7. **Meanwhile**, tell the client to push the Google Play Console verification so Play Protect stops interfering.

---

*This is a corrected version of the action plan. The previous version incorrectly suggested disabling Play Protect from the welcome screen, which is impossible. This correction is documented in `AI_AGENTS_NOTES.md` to prevent future AI agents from repeating the error.*
