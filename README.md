# SecurePay

A three-part smartphone-financing ecosystem (pay-as-you-go device management, M-KOPA style). A device is sold on credit and remains fully usable while payments are current; if the balance goes overdue the device enters a restricted **LOCKED** state until payment resumes.

## Workspace layout

| Path | Stack | Role |
| --- | --- | --- |
| `customer-app/` | Kotlin · Jetpack Compose · M3 | Native Android client / Device Policy Controller (DPC) carried by the end customer |
| `agent-app/` | Kotlin · Jetpack Compose · M3 | Native field-sales enrollment utility for onboarding new customers |
| `dealer-dashboard/` | SvelteKit · TypeScript · TailwindCSS | Admin web console for the dealer/operator |
| `.github/workflows/` | GitHub Actions | CI/CD: build + sign both APKs |

## Shared design system

Dark, charcoal-based Material 3 palette used identically across all three apps:

| Token | Hex | Usage |
| --- | --- | --- |
| Charcoal | `#121212` | App background |
| Surface | `#1E1E1E` | Cards / elevated surfaces |
| Emerald | `#10B981` | `ACTIVE` status accent |
| Amber | `#F59E0B` | `WARNING` (payment due soon) |
| Crimson | `#DC2626` | `LOCKED` / overdue restrictions |

## Shared domain model

A financed account is field-identical across the Kotlin and TypeScript codebases: `id, customerName, nationalId, phoneNumber, imei, deviceModel, planName, totalLoanAmount, amountPaid, remainingBalance, dailyRate, nextPaymentDueEpochMillis, status`. Status is derived purely from the deadline:

- `LOCKED`  — `now >= nextPaymentDueEpochMillis`
- `WARNING` — due within 24h
- `ACTIVE`  — otherwise

## customer-app

MVVM with Coroutines + `StateFlow`. A 1-second ticker re-derives the countdown, status and repayment progress on every emission (`DeviceViewModel`). The **Active Dashboard** shows the live countdown card, progress indicator, remaining balance and a *Simulate Payment Integration* trigger. Crossing into `LOCKED` instantly renders a full-screen, non-dismissible **Lock Overlay** that consumes the Back gesture, exposes only *Emergency Calls* and a *5-Minute Grace Window Request*, and — via `DevicePolicyController` — drops to the secure lock screen and disables USB debugging where the app is provisioned as device owner.

```bash
cd customer-app && ./gradlew :app:assembleRelease
```

## agent-app

Three-step enrollment wizard with immutable draft state (`CustomerEnrollment`) compiled in `EnrollmentViewModel`:

1. **KYC** — customer data entry with field validation
2. **Scanner** — CameraX preview placeholder + manual IMEI fallback
3. **Plan** — M3 dropdown plan selection + down-payment + summary

```bash
cd agent-app && ./gradlew :app:assembleRelease
```

## dealer-dashboard

```bash
cd dealer-dashboard && npm install && npm run dev
```

Sidebar console (Overview, Inventory / IMEI Matrix, Customers, Payment Ledger) with a KPI summary row and an interactive live table whose `[Extend Timer]` and `[Force Remote Lock]` actions call the mock API and mutate the store reactively.

## CI/CD — `.github/workflows/build-apks.yml`

On push / PR to `main`: sets up JDK 17 + Android SDK, builds `customer-app` and `agent-app` **in parallel** (matrix) via the Gradle wrapper, signs each release APK, renames them to `SecurePay-CustomerApp-<short-sha>.apk` / `SecurePay-AgentApp-<short-sha>.apk`, then bundles both into a single downloadable artifact.

Required repository secrets:

| Secret | Purpose |
| --- | --- |
| `SIGNING_KEY` | Base64-encoded release keystore (`.jks`) |
| `KEY_STORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password (set equal to the keystore password if not distinct) |

> The Gradle wrapper JAR is binary and intentionally not committed; CI regenerates it with `gradle wrapper` before building. Run the same command locally once if `./gradlew` reports a missing wrapper JAR.
