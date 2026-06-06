# SecurePay

SecurePay is a three-part smartphone financing ecosystem scaffold:

- `customer-app/` - Kotlin, Jetpack Compose Material 3 customer DPC client.
- `agent-app/` - Kotlin, Jetpack Compose Material 3 field enrollment utility.
- `dealer-dashboard/` - SvelteKit, TypeScript, TailwindCSS dealer console.
- `.github/workflows/build-apks.yml` - CI workflow for parallel Android release builds, APK signing, renaming, and artifact upload.

The mobile apps use MVVM, Kotlin coroutines, `StateFlow`, immutable UI state, and Material Design 3 components. The dashboard mirrors the same product language with a restrained enterprise layout: deep charcoal surfaces, emerald active states, and vivid crimson restricted states.

## Repository Layout

```text
customer-app/
agent-app/
dealer-dashboard/
.github/workflows/
```

## Local Development

Android apps:

```bash
cd customer-app
./gradlew :app:assembleDebug

cd ../agent-app
./gradlew :app:assembleDebug
```

Dealer dashboard:

```bash
cd dealer-dashboard
npm install
npm run dev
```

## Signing Assumptions

The GitHub Actions workflow expects:

- `SIGNING_KEY`: base64-encoded Java keystore file.
- `KEY_STORE_PASSWORD`: keystore password and key password.

The release key alias is configured as `securepay`.
