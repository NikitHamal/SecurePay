# SecurePay Test Checklist
## Quick Verification Before Delivery

---

## 🚀 BEFORE YOU DELIVER - Do These Tests

### 1. Build the App
```bash
cd /home/user/SecurePay/customer-app
./gradlew :app:assembleDebug
```

The APK will be at: `customer-app/app/build/outputs/apk/debug/app-debug.apk`

---

## ✅ VISUAL CHECKS (On Device/Emulator)

### TELECEL Display
- [ ] Open Pay with Mobile Money screen
- [ ] Check TELECEL displays as "TELECEL" (all caps, no "Cash")
- [ ] Verify in both user and agent apps

### Account Screen
- [ ] Open the app
- [ ] Check bottom navigation has 4 tabs: Home, Payments, Account, More
- [ ] Tap Account tab
- [ ] Verify Account screen opens
- [ ] Check account information displays (if logged in)
- [ ] Try password change form
- [ ] Verify form validation works

### Ads Section
- [ ] Open Dashboard
- [ ] Scroll down to find Ads section
- [ ] Verify 3 ads display in carousel
- [ ] Check auto-scrolling works
- [ ] Tap on an ad - verify it tries to open link
- [ ] Check page indicators show correctly

### Bottom Navigation
- [ ] Verify all 4 tabs work: Home, Payments, Account, More
- [ ] Check Account tab has Person icon
- [ ] Verify navigation between tabs is smooth

---

## 📱 NAVIGATION FLOW TESTS

### From Dashboard
- [ ] Tap Account tab → Opens Account screen
- [ ] Tap Payments tab → Opens Payments screen
- [ ] Tap More tab → Opens More screen

### From More Screen
- [ ] Tap "My Account" → Opens Account screen
- [ ] Tap other options → Verify they work

### From Account Screen
- [ ] Tap back button → Returns to previous screen
- [ ] Tap other bottom tabs → Navigation works

---

## 🎨 UI/UX CHECKS

### Colors
- [ ] Check all buttons use Gold color
- [ ] Verify backgrounds use Charcoal theme
- [ ] No hardcoded green colors visible
- [ ] Text colors are consistent

### Text Wrapping
- [ ] "Pay with Mobile Money" button text doesn't wrap
- [ ] All long text is properly truncated with "..."
- [ ] No text overflow visible

### Layout
- [ ] All screens scroll properly
- [ ] No elements overlap
- [ ] Proper spacing between elements
- [ ] Icons are visible and correct

---

## ⚡ EXISTING FUNCTIONALITY CHECKS

### Payments
- [ ] Open Pay with Mobile Money
- [ ] Select provider (MTN, TELECEL, AirtelTigo)
- [ ] Verify TELECEL shows as "TELECEL"
- [ ] Try entering phone number
- [ ] Try entering amount
- [ ] Verify form validation works

### Device Status
- [ ] Check Dashboard shows device status correctly
- [ ] Verify Sync Status button works
- [ ] Check History and Updates buttons work

### Security
- [ ] Verify app doesn't crash on startup
- [ ] Check permissions dialog appears if needed
- [ ] Verify device locking still works (if applicable)

---

## 🐞 COMMON ISSUES TO CHECK

### If App Crashes on Startup:
1. Check all imports are correct
2. Verify all new files are in correct packages
3. Check build.gradle dependencies are synced

### If Navigation Doesn't Work:
1. Verify all screens are added to SecurePayApp navigation
2. Check all callbacks are properly passed
3. Verify bottom bar onClick handlers are set

### If Ads Don't Show:
1. Check AdSlideSection is called in DashboardScreen
2. Verify mock ads data is present
3. Check AdSlideView is properly imported

---

## 📝 FILES CREATED/MODIFIED

### New Files:
- [ ] `customer-app/app/src/main/java/com/touchbase/user/ui/account/AccountScreen.kt`
- [ ] `customer-app/app/src/main/java/com/touchbase/user/ui/account/AccountViewModel.kt`
- [ ] `customer-app/app/src/main/java/com/touchbase/user/data/model/AdModel.kt`
- [ ] `customer-app/app/src/main/java/com/touchbase/user/data/repository/AdRepository.kt`
- [ ] `customer-app/app/src/main/java/com/touchbase/user/ui/components/AdSlideView.kt`

### Modified Files:
- [ ] `customer-app/app/src/main/java/com/touchbase/user/data/model/PaystackModels.kt` (TELECEL fix)
- [ ] `agent-app/app/src/main/java/com/touchbase/agent/ui/payments/AgentPayWithMoMoDialog.kt` (TELECEL fix)
- [ ] `customer-app/app/src/main/java/com/touchbase/user/ui/navigation/Screen.kt` (Account screen)
- [ ] `customer-app/app/src/main/java/com/touchbase/user/ui/SecurePayApp.kt` (navigation)
- [ ] `customer-app/app/src/main/java/com/touchbase/user/ui/components/CustomerBottomBar.kt` (Account tab)
- [ ] `customer-app/app/src/main/java/com/touchbase/user/ui/dashboard/DashboardScreen.kt` (Ads + Account)
- [ ] `customer-app/app/src/main/java/com/touchbase/user/ui/more/MoreScreen.kt` (Account link)
- [ ] `customer-app/app/build.gradle.kts` (dependencies)

---

## ✅ FINAL CHECKLIST

Before delivering to client:
- [ ] App builds successfully
- [ ] All visual checks pass
- [ ] All navigation works
- [ ] All existing functionality preserved
- [ ] No crashes or errors
- [ ] TELECEL displays correctly
- [ ] Account screen works
- [ ] Ads section visible and functional

---

## 🎉 YOU'RE READY TO DELIVER!

If all checks pass, build the APK and send it to your client. You've addressed all their requirements:

✅ TELECEL displays as "TELECEL"
✅ Account tab and screen added
✅ Password change functionality
✅ Ads section with 3-slide carousel
✅ All UI/UX improvements

**Good luck! Your brother is counting on you.**
