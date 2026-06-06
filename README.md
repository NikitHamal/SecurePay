# SecurePay

SecurePay is a three-part smartphone financing ecosystem scaffolded as a
multi-app workspace:

```text
customer-app/        Kotlin + Jetpack Compose M3 customer device client
agent-app/           Kotlin + Jetpack Compose M3 field enrollment utility
dealer-dashboard/    SvelteKit + TypeScript + TailwindCSS dealer console
.github/workflows/   GitHub Actions APK build and signing automation
```

## Mobile Apps

Both Android apps are independent Gradle projects with their own wrapper files.
They target Java 17, Android SDK 35, Kotlin, MVVM, Coroutines, StateFlow, and
Jetpack Compose Material 3.

```bash
cd customer-app
./gradlew assembleRelease

cd ../agent-app
./gradlew assembleRelease
```

The customer app models a financed device countdown, transitions through
`ACTIVE`, `WARNING`, and `LOCKED`, and includes guarded DevicePolicyManager hooks
for lock-task mode, USB debugging disablement, status-bar restriction, back
blocking, and emergency/grace-window flows.

The agent app implements a three-step enrollment wizard for KYC, CameraX-backed
IMEI capture, and downpayment plan selection. The wizard compiles immutable state
into a network-ready enrollment payload.

## Dealer Dashboard

The web console is a SvelteKit app with a Material Design 3-inspired enterprise
layout, KPI analytics, IMEI inventory, customer status tracking, a payment ledger,
and live mock command actions.

```bash
cd dealer-dashboard
pnpm install
pnpm dev
pnpm build
```

## CI/CD

`.github/workflows/build-apks.yml` builds both Android apps in parallel, signs
their release APKs with Android SDK signing tools, renames them using the short
commit SHA, and uploads both binaries as a single workflow artifact.
