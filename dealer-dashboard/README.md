# SecurePay Dealer Dashboard - production-hardened source

SvelteKit/Cloudflare Pages dashboard and API for the Ghana phone-financing deployment.

## What changed

- QR provisioning reads signed APK metadata from R2 `latest.json`.
- Each QR is pinned to an immutable HTTPS APK and exact SHA-256 checksum.
- QR version is now `3` and includes Android Device Owner component, Wi-Fi security type, one-time admin extras, and security-policy extras.
- Dealer-configurable EFRP policy is available in Inventory -> Production security policy.
- Wi-Fi passwords are no longer stored in D1.
- Activation requires both a 256-bit one-time token and six-digit code.
- Successful activation issues a per-device API HMAC secret stored against the financed account.
- Registered device requests are verified with the per-device secret when possible; the global APK HMAC secret is only a bootstrap/fallback path.
- HMAC requests have timestamp and nonce replay rejection.
- Customer app uses device-scoped account/payment endpoints instead of dealer-only endpoints.
- Device/account/heartbeat/payment binding checks include both account ID and IMEI.
- Final payment automatically approves customer-app release, and dealers/agents can trigger explicit release for test/early settlement.
- Customer-app update endpoint supports a minimum supported version gate.
- Ghana locale, `GHS`, `+233`, Mobile Money naming, and integer-pesewa formatting are used.
- Demo seeding is authenticated and disabled unless explicitly enabled.

## Required Cloudflare bindings

`wrangler.toml` expects:

- D1 binding: `DB`
- R2 binding: `R2`

Replace the example/previous D1 `database_id` and bucket values when deploying to a different Cloudflare account.

Required worker secrets/variables:

```text
JWT_SECRET=<long random value>
HMAC_SECRET=<different long random value; same value used by Android CI for bootstrap traffic>
ALLOW_DEMO_SEED=false
FRP_ACCOUNT_IDS=
CUSTOMER_APP_MIN_SUPPORTED_VERSION_CODE=4
```

`FRP_ACCOUNT_IDS` is an optional comma-separated fallback. Prefer saving dealer-specific numeric Google account IDs from the dashboard UI instead.

Rotate any value that appeared in the original uploaded `.env`; it must be treated as exposed.

## Database migration

For a brand-new environment, apply the current base schema:

```bash
npx wrangler d1 execute securepay-db --remote --file=migrations/0001_initial.sql
```

The base schema already contains the release-lifecycle and security-hardening columns. Do not run the `20260619_*` ALTER migrations against a fresh database created from the current `0001_initial.sql`, because the columns already exist.

For an existing database created from an older dashboard version, take a backup first, inspect which columns already exist, then apply only the missing migrations in order:

```bash
npx wrangler d1 execute securepay-db --remote --file=migrations/20260618_provisioning_v2.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260619_release_lifecycle.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260619_security_hardening.sql
```

Do not run `0001_initial.sql` against a populated database.

## EFRP production policy

EFRP requires Google numeric account IDs, not plain email addresses. Configure the dealer's permanent admin account IDs in Inventory -> Production security policy before generating production QRs. Every QR generated after a policy change embeds the current policy version and account IDs in the Android admin extras.

Recommended release flow:

1. Dealer configures EFRP numeric Google account IDs.
2. Agent generates a fresh QR and confirms the agent app shows EFRP enabled.
3. Customer app applies the base loan policy immediately after Device Owner provisioning.
4. While the loan is active, Settings factory reset, app removal, ADB/developer features, unknown-source install, and account-modification paths are restricted where Android permits.
5. After full payment or explicit dealer release, the app clears EFRP/restrictions, clears Device Owner/admin state, calls `/api/device/release-complete`, and can be uninstalled.

## Install, validate, build

```bash
npm ci
npm run check
npm run build
```

Expected result for this source: `0 errors and 0 warnings`, followed by a successful Cloudflare adapter build.

## Deploy order

1. Rotate production secrets.
2. Apply the D1 migrations.
3. Set Cloudflare secrets/bindings and deploy this dashboard.
4. Configure Android GitHub release secrets.
5. Build the customer APK with the permanent release key.
6. Let CI upload the immutable APK and publish `latest.json` last.
7. Configure EFRP numeric Google IDs in Inventory -> Production security policy.
8. Generate a new provisioning QR. Old QRs/APKs must not be reused.

## Money storage

All persisted monetary values are integer pesewas. For example, `GHc 125.50` is stored as `12550`. API payment amounts must be positive integers and cannot exceed the outstanding balance.

## Security boundary

This dashboard and DPC implement the strongest practical cross-OEM Android Enterprise path without Samsung Knox Guard: Device Owner, EFRP, per-device HMAC, nonce replay rejection, one-time provisioning tokens, release lifecycle, and update control. This does not guarantee survival against firmware flashing, service-center tooling, hardware attacks, or future OEM vulnerabilities. Samsung-only programs that require stronger OEM-backed financed-device controls should add Knox Guard as an optional premium layer.
