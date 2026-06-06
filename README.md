# SecurePay

A modular, three-part **smartphone-financing ecosystem** (M-KOPA-style device financing).
A handset is sold on installment credit; the customer app acts as a Device Policy
Controller (DPC) that reflects payment status, field agents enroll customers, and dealers
monitor the fleet from a web console.

## Workspace layout

```
securepay/
├── customer-app/        # Kotlin + Jetpack Compose (Native Android client / DPC)
├── agent-app/           # Kotlin + Jetpack Compose (Native field enrollment utility)
├── dealer-dashboard/    # SvelteKit + TypeScript + TailwindCSS (Admin web console)
└── .github/workflows/   # CI/CD automation (build-apks.yml)
```

## Shared domain model

All three apps align on a single device/loan model so data is consistent across platforms:

| Field                | Type            | Notes                                   |
| -------------------- | --------------- | --------------------------------------- |
| `DeviceStatus`       | enum            | `ACTIVE` · `WARNING` · `LOCKED`         |
| `nextDueEpochMillis` | timestamp       | Payment deadline driving the countdown  |
| `serverEpochMillis`  | timestamp       | Mock API "now" the reactive clock ticks from |
| balance fields       | money           | `totalLoanAmount`, `amountPaid`, `remainingBalance`, `dailyInstallment` |

**State rule** (evaluated against a live ticking clock):

- `LOCKED`  — `now >= nextDueEpochMillis`
- `WARNING` — within the warning threshold (default 24h) of the deadline
- `ACTIVE`  — otherwise

## Design language

Material Design 3, dark-first, with a cohesive professional palette:

| Token              | Hex       | Usage                          |
| ------------------ | --------- | ------------------------------ |
| Deep Charcoal      | `#121212` | Background                     |
| Surface            | `#1E1E1E` | Cards / panels                 |
| Emerald            | `#10B981` | `ACTIVE` accent                |
| Amber              | `#F59E0B` | `WARNING`                      |
| Vivid Crimson      | `#DC2626` | `LOCKED` / overdue restriction |

## Running each app

### customer-app / agent-app (Android)

```bash
cd customer-app   # or agent-app
gradle wrapper --gradle-version 8.9   # one-time, materialises ./gradlew
./gradlew :app:assembleRelease
```

Requires JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).

### dealer-dashboard (web)

```bash
cd dealer-dashboard
npm install
npm run dev        # local dev server
npm run check      # type-check
npm run build      # production build
```

## CI/CD

`.github/workflows/build-apks.yml` runs on every push / PR to `main`:

1. Sets up JDK 17 + Android SDK + Gradle 8.9.
2. **Compiles `customer-app` and `agent-app` in parallel** (matrix strategy).
3. Signs each release APK using the `SIGNING_KEY` / `KEY_STORE_PASSWORD` secrets.
4. Renames the binaries to `SecurePay-CustomerApp-<short-sha>.apk` and
   `SecurePay-AgentApp-<short-sha>.apk`.
5. Bundles both signed APKs into a single downloadable workflow artifact.

### Required repository secrets

| Secret               | Purpose                                   |
| -------------------- | ----------------------------------------- |
| `SIGNING_KEY`        | Base64-encoded release keystore (`.jks`)  |
| `KEY_STORE_PASSWORD` | Keystore password                         |
| `KEY_ALIAS`          | Signing key alias                         |
| `KEY_PASSWORD`       | Key password                              |
