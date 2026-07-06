# 🛡️ SecurePay: Device Financing & Loan Management System

SecurePay is an enterprise-grade loan management platform designed for phone dealers to sell devices on EMI (Equated Monthly Installments). The system provides a complete lifecycle—from agent-led customer enrollment to automated device locking upon payment default.

## 🚀 System Architecture

The platform is composed of three primary modules:

### 1. Dealer Dashboard (Backend & Admin UI)
- **Tech Stack:** SvelteKit, TypeScript, Tailwind CSS.
- **Infrastructure:** Cloudflare Workers, D1 (SQL Database), R2 (Object Storage).
- **Capabilities:** 
  - Portfolio overview and KPI tracking.
  - Customer KYC and loan management.
  - Device inventory tracking.
  - Remote lock/unlock control.
  - Automated loan status calculations.

### 2. Agent App (Enrollment Tool)
- **Tech Stack:** Kotlin, Jetpack Compose.
- **Capabilities:**
  - Digital KYC (Photo capture, National ID upload).
  - Device pairing via IMEI.
  - Loan plan selection and customization.
  - Provisioning token generation for customer devices.

### 3. Customer App (Device Controller)
- **Tech Stack:** Kotlin, Jetpack Compose, Android DPC.
- **Capabilities:**
  - **Deep System Integration:** Acts as a Device Owner to prevent unauthorized factory resets, ADB access, and app uninstallation.
  - **Kiosk Mode:** Locks the device into a restricted state when payments are overdue.
  - **FRP Management:** Sets Factory Reset Protection policies to ensure the device remains secured.
  - **Real-time Sync:** Heartbeat system to keep device status in sync with the server.

---

## 🛠️ Installation & Setup

### Backend (Dealer Dashboard)
1. **Requirements:** [Wrangler CLI](https://developers.cloudflare.com/workers/wrangler/), Cloudflare Account.
2. **Database Setup:**
   ```bash
   wrangler d1 create securepay-db
   wrangler d1 execute securepay-db --file=./schema.sql
   ```
3. **R2 Bucket Setup:**
   ```bash
   wrangler r2 bucket create securepay-kyc
   ```
4. **Deployment:**
   ```bash
   npm install
   npm run build
   wrangler deploy
   ```

### Android Apps (Agent & Customer)
1. Open both `agent-app` and `customer-app` folders in **Android Studio**.
2. Configure `local.properties` with your API keys:
   - `API_BASE_URL`
   - `HMAC_SECRET`
   - `FCM_API_KEY`
3. Build and install the APKs.

---

## 🔒 Security Model

SecurePay employs a multi-layered security approach:
- **Dealer Auth:** JWT-based authentication with bcrypt password hashing.
- **Device Auth:** HMAC-SHA256 signatures on every request, utilizing device-specific secrets, nonces, and timestamps to prevent replay attacks.
- **Anti-Tamper:** The Customer app uses `DevicePolicyManager` to restrict `ADB_ENABLED`, `DISALLOW_FACTORY_RESET`, and `DISALLOW_UNINSTALL_APPS`.
- **Provisioning:** A secure token-based flow ensures only authorized devices are enrolled into the system.

## 📅 Loan Lifecycle
`Inventory` $\rightarrow$ `Agent Enrollment (KYC)` $\rightarrow$ `Device Provisioning` $\rightarrow$ `Active Loan` $\rightarrow$ `Payment Tracking` $\rightarrow$ `Overdue Lock` $\rightarrow$ `Payment` $\rightarrow$ `Remote Unlock` $\rightarrow$ `Final Release`.


## July 6 location/stolen-device patch

The customer DPC and dashboard API were updated so stolen-device tracking works after a dealer flags a device as stolen:

- Customer app location upload now uses a typed Kotlin serialization payload instead of `Map<String, Any>`.
- Every location upload includes both `accountId` and `imei`, allowing the dashboard to resolve the per-device HMAC secret.
- Tracking pings are stored in a small persistent queue and uploaded in batches when network is available.
- Device Owner policy now attempts to grant fine/coarse/background location and notification permissions for the managed app.
- The tracking worker passes `accountId` to `/api/device/check`, so per-device HMAC verification works after activation.
- The service removes location callbacks on shutdown and reports real battery percentage.

Build/test note: the source package does not include a downloaded Gradle distribution in this environment, so compile validation must be run in Android Studio or a CI runner with internet/cached Gradle.
