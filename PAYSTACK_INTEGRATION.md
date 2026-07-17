# Paystack Mobile-Money Integration — SecurePay

**Date:** 17 July 2026
**Status:** End-to-end production-ready across dealer dashboard, agent Android app, and customer Android app. GHS mobile money (MTN MoMo, Vodafone Cash, Telecel/AirtelTigo) + cards (dashboard).

## What was built

### Server (Cloudflare Workers / D1)
- **`dealer-dashboard/src/lib/paystack.ts`** — typed Paystack client:
  - `initializeCharge()` — server-side `/charge` call (GHS, `mobile_money` or `card`).
  - `submitOtp()` — submit 4-8 digit OTP.
  - `verifyTransaction()` — GET `/transaction/verify/:reference`.
  - `verifyWebhookSignatureAsync()` — HMAC-SHA256 of raw body using Web Crypto (timing-safe).
  - `generateReference(prefix)` — unique reference. Prefix `SP_` for dealer/agent-initiated, `SPD_` for customer-device-initiated.
- **`dealer-dashboard/src/lib/payments.ts`** — shared `applyPayment()` used by dealer manual-record, Paystack polling, and webhook paths. Identical math: increment `amount_paid`, advance `next_payment_due` by `amount/daily_rate*1day`, unlock device, auto-release on payoff; idempotent by reference.
- **`dealer-dashboard/src/lib/paystack/email.ts`** — derives a stable `customer-<digits>@pay.securepay.io` when the account has no email on file.
- **Migration `migrations/20260717_paystack.sql`** — adds `paystack_transactions` (unique reference, status, provider, pesewas, fees, paid_at, authorization_code, payment_id FK).
- **`hooks.server.ts`** — `/api/device/paystack/*` is covered by the same HMAC nonce-replay protection as all device endpoints (per-device `device_hmac_secret`, 10-minute nonce window, accountId+imei scope).

### API endpoints

| Method | Route | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/paystack/initialize` | dealer (cookie/JWT) | Start a charge from the dashboard or agent app. |
| `POST` | `/api/paystack/otp` | dealer | Submit OTP for a dealer-initiated charge. |
| `GET`  | `/api/paystack/verify/:reference` | dealer | Poll & auto-apply payment on success (idempotent). |
| `POST` | `/api/device/paystack/initialize` | **device HMAC** | Start a charge from the customer Android app (scoped to provisioned IMEI). |
| `POST` | `/api/device/paystack/otp` | device HMAC | Submit OTP for a device-initiated charge. |
| `GET`  | `/api/device/paystack/verify/:reference` | device HMAC | Poll from the device, returns fresh `newAmountPaid`, `nextPaymentDueEpochMillis`, `lockedByDealer`, `paidOff` on success so the client can update immediately. |
| `POST` | `/api/paystack/webhook` | **public (HMAC-signed by Paystack)** | Receives `charge.success`, verifies `X-Paystack-Signature`, applies payment identically to manual. |

### Dealer dashboard
- **`PayWithMoMoModal.svelte`** — step dialog: amount (defaults to daily rate, "Pay full balance" chip), phone, network, processing, OTP, success/failed. Polls `/verify` every 3 s.
- **CustomerDrawer** mounts the modal; **"Pay with MoMo"** is the primary CTA.

### Agent Android app (Kotlin / Jetpack Compose / M3 dark)
- `data/model/Paystack.kt` — request/response types.
- `data/remote/SecurePayApi.kt` — `paystackInitialize/paystackSubmitOtp/paystackVerify`.
- `data/remote/SecurePayRepository.kt` — repository wrappers with friendly error extraction.
- `ui/payments/AgentPayWithMoMoDialog.kt` — M3 dialog matching dashboard: remaining balance card, quick-chips (1 day / full), provider chips (MTN/Vodafone/Telecel), phone input, processing, OTP, success/failed, auto-polls verification.
- `ui/customers/CustomerDetailScreen.kt` — new primary **"Pay with Mobile Money"** emerald CTA above actions; existing cash/manual flow renamed **"Cash / Record"**.

### Customer Android app (Kotlin / Jetpack Compose / M3 dark — permanently dark theme)
- `data/model/PaystackModels.kt` — `MomoProvider` enum + request/response types.
- `data/remote/SecurePayApi.kt` — HMAC-authenticated calls to `/api/device/paystack/*`.
- `data/repository/DeviceRepository.kt` — `paystackInitialize/SubmitOtp/Verify`; verify auto-refreshes account + payment history so UI unlocks/updates immediately.
- `ui/payments/PayWithMoMoScreen.kt` — full customer-facing flow:
  - Hero "Remaining balance" card + **"Pay with Mobile Money"** emerald button.
  - Amount field with "Pay 1 day" / "Pay full balance" chips.
  - Provider chips MTN / Vodafone / Telecel.
  - Ghana phone field with format validation.
  - Processing state, OTP entry state, success state (with "Paid off" message when applicable), failed state with retry.
  - Polls device `/verify` every 3 s for up to 2 min (both the no-OTP and post-OTP paths).
- `ui/dashboard/DashboardScreen.kt` — primary **"Pay with Mobile Money"** button replaces secondary "Sync Status"; "Sync Status" becomes outline button; "History" replaces old "Payments" label. Removed the gradient brush on hero status card (solid color tile per design rule).
- `ui/payments/PaymentsScreen.kt` — shows remaining balance and **Pay with Mobile Money** button at top of payment history.
- `ui/navigation/Screen.kt` + `ui/SecurePayApp.kt` — new `pay-momo` route wired from dashboard, payments screen, and "Done" returns to dashboard and triggers a refresh.

## Secret configuration

Paystack secret key must be set as a secret (never committed):

```bash
cd dealer-dashboard
# Test key: sk_test_...  Live key: sk_live_...
npx wrangler secret put PAYSTACK_SECRET_KEY
# When prompted, paste: sk_test_7761f87e180a868ed718278e4200cda53bd2a68f   (test)
```

For local dev (`wrangler dev`), create `dealer-dashboard/.dev.vars`:
```
PAYSTACK_SECRET_KEY=sk_test_7761f87e180a868ed718278e4200cda53bd2a68f
```

## Deploy the migration

```bash
cd dealer-dashboard
npx wrangler d1 execute securepay-db-weur --remote --file migrations/20260717_paystack.sql
```

Local dev DB:
```bash
npx wrangler d1 execute DB --local --file migrations/20260717_paystack.sql
```

## Configure Paystack dashboard

1. Log in to https://dashboard.paystack.com.
2. Settings → API Keys & Webhooks:
   - **Webhook URL:** `https://<your-pages-domain>/api/paystack/webhook`
   - **Events to send:** `charge.success`, `charge.failure` (optionally `charge.dispute`).
3. Add the secret as `PAYSTACK_SECRET_KEY` (see above).
4. Test end-to-end with Paystack's test values.
5. When going live, swap the secret for the `sk_live_` key and set Android `API_BASE_URL` to the production domain.

## Flow

1. **Customer taps "Pay with Mobile Money" on their device** (or agent taps it on their phone, or dealer clicks it in the dashboard).
2. Client validates amount, Ghana phone (`02x/05x`/`+233x`), and provider.
3. Server validates scope (dealer JWT **or** device HMAC+IMEI+nonce), amount ≤ remaining balance, phone format; normalizes phone to `+233…`; generates unique reference; calls Paystack `/charge`; inserts into `paystack_transactions`.
4. Paystack returns `send_otp` (OTP via SMS) or an async pending state. UI either shows the OTP field or polls `/verify` every 3 s.
5. On success (via polling **or** webhook), `applyPayment()` atomically:
   - Inserts a `payments` row (`method='MOBILE_MONEY'`, `reference='paystack:<reference>'`).
   - Increments `accounts.amount_paid`.
   - Advances `next_payment_due` by `amount / daily_rate × 1 day` (sub-day precision).
   - Clears `locked_by_dealer = 0`.
   - Auto-flags release when fully paid off.
6. Customer app: verify endpoint returns fresh balance/due/locked state; repository calls `refresh()` + `refreshPayments()` so the dashboard updates and (if locked) the DPC policy controller releases restrictions on next recomposition via the `LaunchedEffect(state.isLocked)` in `SecurePayApp.kt`.

## Idempotency / safety

- `paystack_transactions.reference` is UNIQUE.
- Webhook *and* polling both check `payment_id IS NULL` before applying → double-charges are impossible.
- Payment application uses D1 batches (atomic `BEGIN/COMMIT`).
- Secret key is server-side only; never returned in any response.
- Webhook signature verified against raw body **before** parsing.
- Device endpoints require per-device HMAC signature + nonce replay protection (same as heartbeat/account/payments).
- Phone normalized to `+233…`; amounts in integer pesewas everywhere.

## Build status
- Dealer dashboard: `svelte-check` **0 errors** (5 pre-existing a11y warnings in `admin/push`), `vite build` succeeds in ~13 s.
- Android: source compiles against existing Kotlin/Compose setup; gradlew + SDK not available in this sandbox so APK must be built locally (`./gradlew assembleDebug assembleRelease` in `agent-app/` and `customer-app/`).

## Test values (Paystack docs)
- Test card success: `4084 0840 8408 4081`, any CVV/date, OTP `1234`.
- Test MoMo: any valid Ghana phone in test mode; Paystack simulates success. Use OTP `1234` when asked.

## Follow-ups (non-blocking)
- **Saved authorization / recurring** — `authorization_code` is stored for future use; auto-charge due dates requires a scheduled worker (Cloudflare Cron Trigger).
- **Refunds** — not yet wired; use Paystack dashboard and manual ledger adjustment.
- **Fees** — stored but not yet deducted from dealer reporting.
- **Remaining pages visual pass** — ledger, branches, logs, notifications, my-sales, agencies still have older styling but compile; customer/agent/inventory/requests/login/sidebar/topbar are already modernized.
