# SecurePay Dealer Dashboard — production-fixed source

SvelteKit/Cloudflare Pages dashboard and API for the Ghana phone-financing deployment.

## What changed

- QR provisioning reads signed APK metadata from R2 `latest.json`.
- Each QR is pinned to an immutable HTTPS APK and exact SHA-256 checksum.
- QR includes Android Device Owner component, Wi-Fi security type, and one-time admin extras.
- Wi-Fi passwords are no longer stored in D1.
- Activation requires both a 256-bit one-time token and six-digit code.
- HMAC requests have timestamp and nonce replay rejection.
- Customer app uses device-scoped account/payment endpoints instead of dealer-only endpoints.
- Device/account/heartbeat/payment binding checks include both account ID and IMEI.
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
HMAC_SECRET=<different long random value; same value used by Android CI>
ALLOW_DEMO_SEED=false
```

Rotate any value that appeared in the original uploaded `.env`; it must be treated as exposed.

## Database migration

Apply the base schema for a new environment, then the production migration:

```bash
npx wrangler d1 execute securepay-db --remote --file=migrations/0001_initial.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260618_provisioning_v2.sql
```

For an existing database, apply only the migration after taking a backup and reviewing current schema/data.

## Install, validate, build

```bash
npm ci
npm run check
npm run build
```

Expected result for this source: `0 errors and 0 warnings`, followed by a successful Cloudflare adapter build.

## Deploy order

1. Rotate production secrets.
2. Apply the D1 migration.
3. Set Cloudflare secrets/bindings and deploy this dashboard.
4. Configure Android GitHub release secrets.
5. Build the fixed customer APK with the permanent release key.
6. Let CI upload the immutable APK and publish `latest.json` last.
7. Generate a new provisioning QR. Old QRs/APKs must not be reused.

## Money storage

All persisted monetary values are integer pesewas. For example, `GH₵ 125.50` is stored as `12550`. API payment amounts must be positive integers and cannot exceed the outstanding balance.
