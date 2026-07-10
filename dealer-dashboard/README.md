# Touch Base dashboard and device API

SvelteKit/TypeScript application deployed to Cloudflare Pages/Workers with D1 and R2. It provides the dealer console plus the APIs consumed by the agent and customer Android apps.

## Production pass highlights

- Enforced four-level visibility: Super Admin -> DSL Agency -> Branch Admin -> Agent.
- Added resource-level scope checks to accounts, devices, payments, ledger, locations, KYC, agents, branches and provisioning.
- Shortened authenticated sessions, moved browser auth to `HttpOnly`, `Secure`, `SameSite=Strict` cookies, stopped returning/persisting browser bearer tokens, and retained bearer tokens for Android clients.
- Added rate limits for login, registration and sensitive mutations.
- Hardened activation and recovery so an enrolled device receives and can recover its per-device HMAC secret only when the provisioning token, activation code and exact 15-digit IMEI match, without leaking customer data from the bootstrap check endpoint.
- Added Didit hosted verification-session creation and signed/idempotent webhook processing.
- Added stolen-location ingestion/read scope, device update metadata, health checks and security-policy inheritance.
- Disabled destructive demo seeding unless explicitly enabled.
- Removed bundled APKs and all committed runtime credentials.

The complete findings, limitations and file-level changes are in `../PRODUCTION_AUDIT_2026-07-10.md`.

## Required Cloudflare bindings

`wrangler.toml` expects:

- D1: `DB`
- R2: `R2`

Replace the sample D1 database ID, bucket and Firebase project values for the client's Cloudflare/Firebase accounts.

Set secrets with Wrangler rather than committing `.env` or `.dev.vars`:

```text
JWT_SECRET=<long random value>
HMAC_SECRET=<different long random bootstrap value>
DIDIT_API_KEY=<Didit server API key>
DIDIT_WEBHOOK_SECRET=<Didit webhook secret>
DIDIT_WORKFLOW_ID=<approved Didit workflow ID>
DIDIT_CALLBACK_URL=https://your-dashboard.example/api/webhooks/didit
ALLOW_DEMO_SEED=false
FRP_ACCOUNT_IDS=
CUSTOMER_APP_MIN_SUPPORTED_VERSION_CODE=18
```

Firebase service-account material (`FCM_SERVICE_ACCOUNT_EMAIL`, `FCM_SERVICE_ACCOUNT_PRIVATE_KEY`, and `FCM_PROJECT_ID`) must also be supplied through the deployment secret mechanism used by this project. Rotate every credential that was present in a previous source archive.

## Install and validate

```bash
npm ci
npm run check
npm run build
```

## New database deployment

Apply every migration in filename order:

```bash
npx wrangler d1 execute securepay-db --remote --file=migrations/0001_initial.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/0002_multi_tenant.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260618_provisioning_v2.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260619_release_lifecycle.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260619_security_hardening.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260624_kyc_photos.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260625_fcm_push.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260626_custom_plans.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260627_dangling_fk_cleanup.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260627_provisioning_tokens_fk.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260702_device_logs.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260706_stolen_tracking_location.sql
npx wrangler d1 execute securepay-db --remote --file=migrations/20260710_production_hardening.sql
```

The migration chain was replayed against a fresh SQLite database and passed `PRAGMA foreign_key_check` during this audit.

## Existing database warning

Back up D1 before changing it. An older `20260626_custom_plans.sql` rebuilt `accounts` without preserving the multi-tenant/KYC columns introduced earlier. The corrected migration fixes fresh deployments, but it cannot restore columns already lost in a live database.

For a database that already ran the broken migration:

1. Export/backup the remote D1 database.
2. Inspect `PRAGMA table_info(accounts);` and affected data.
3. Review and adapt `manual-migrations/repair_accounts_after_20260626.sql`.
4. Execute the repair in staging first.
5. Run `20260710_production_hardening.sql`.
6. Verify counts, hierarchy assignments, KYC state and `PRAGMA foreign_key_check` before production traffic.

Do not blindly run the repair against a healthy schema.

## Create the first Super Admin

```bash
node scripts/create-super-admin.mjs \
  --name 'Touch Base Owner' \
  --email 'owner@example.com' \
  --password 'use-a-password-manager-generated-password'
```

The script prints SQL containing a bcrypt cost-12 hash. Review it, then execute that SQL against D1 through an authenticated administrative workflow. It never writes a plaintext password to the database.

Create agency owners, branch administrators or directly provisioned agents after their agency/branch rows exist:

```bash
node scripts/create-staff.mjs \
  --role BRANCH_ADMIN \
  --name 'Accra Branch Admin' \
  --email 'branch@example.com' \
  --password 'use-a-password-manager-generated-password' \
  --approved-by 'DLR-SUPERADMINID' \
  --agency 'AGY-XXXXXXXX' \
  --branch 'BR-XXXXXXXX'
```

The script emits reviewed SQL and updates `agencies.owner_id` or `branches.admin_id` when applicable. Normal field agents should still use the self-registration and approval workflow.

## Didit Ghana Card/KYC flow

1. An authorized operator opens a customer and requests verification.
2. The server creates a Didit hosted session using its server-side API key and stores the returned session ID against the customer.
3. The browser opens the hosted verification URL; the API key is never sent to the Android/web client.
4. Didit calls `/api/webhooks/didit`.
5. The webhook verifies the v2 timestamp and HMAC signature, rejects replay/duplicate events, confirms the session/account binding and updates KYC status.

Configure the Didit workflow for the documents and liveness checks legally approved for the Ghana deployment. A provider decision is an input to the lender's KYC process, not a substitute for licensing, consent, retention rules or manual exception handling.

## Customer APK update publishing

Publish immutable, release-signed APK objects to R2 and update `latest.json` only after the APK upload succeeds. Metadata must include the HTTPS URL, version code and SHA-256 checksum expected by the customer app. The app rejects insecure URLs, excessive files, checksum mismatch and signing-certificate mismatch.

After `latest.json` is live, send the best-effort FCM update trigger from an authenticated Super Admin session:

```bash
curl -fsS -X POST -b /path/to/super-admin-cookie.txt \
  https://YOUR-DOMAIN/api/admin/push-customer-update
```

Customer apps subscribe to the `tb-customer-updates` topic. FCM delivery is not a hard real-time guarantee, so successful heartbeat and periodic WorkManager checks remain fallback paths.

## Deployment order

1. Back up and migrate D1.
2. Set/rotate all Cloudflare secrets and deploy the dashboard/API.
3. Confirm `/api/health` reports the expected build/routes.
4. Create the Super Admin and configure agency/branch hierarchy.
5. Configure Didit/Firebase callbacks and production domains.
6. Build both Android apps with the permanent release key.
7. Publish customer update metadata.
8. Provision only with fresh tokens generated by the deployed API.
9. Complete the physical-device acceptance matrix in `../DEPLOYMENT_RUNBOOK.md`.

## Non-goals

This code does not implement covert tracking, root persistence, custom-ROM survival or a guarantee against firmware flashing. Stronger guarantees require an OEM/Android Enterprise commercial program, contractual/legal controls and model-specific validation.
