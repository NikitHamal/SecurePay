# SecurePay — Project Guide for AI Agents

## What is SecurePay?

SecurePay is a **pay-as-you-go smartphone financing platform** (M-KOPA-style). A phone dealer sells devices on credit. The customer pays a small daily amount. If they stop paying, the device locks. If they resume, it unlocks. The app survives factory resets via Android Device Owner / FRP integration.

**Three apps, one platform:**

| App | Stack | Purpose |
|-----|-------|---------|
| `customer-app/` | Kotlin, Jetpack Compose, M3 | Customer's phone — DPC lock/unlock, daily payment UI |
| `agent-app/` | Kotlin, Jetpack Compose, M3 | Dealer's field tool — enroll new customers, scan IMEIs |
| `dealer-dashboard/` | SvelteKit, TypeScript, TailwindCSS | Dealer web console — manage accounts, view ledger, force-lock |

## Architecture

```
┌─────────────────┐  ┌─────────────────┐  ┌──────────────────────┐
│  customer-app    │  │   agent-app     │  │  dealer-dashboard    │
│  (Android DPC)  │  │  (Android)      │  │  (SvelteKit SPA)     │
└────────┬────────┘  └────────┬────────┘  └──────────┬───────────┘
         │                    │                      │
         └────────────────────┼──────────────────────┘
                              │
                    ┌─────────▼─────────┐
                    │   SecurePay API   │
                    │  (SvelteKit API   │
                    │   routes + Turso) │
                    └─────────┬─────────┘
                              │
                    ┌─────────▼─────────┐
                    │   Turso (SQLite)  │
                    │   (cloud DB)      │
                    └───────────────────┘
```

## Database Schema (Turso)

```sql
-- Core tables
CREATE TABLE dealers (
  id          TEXT PRIMARY KEY,          -- UUID
  name        TEXT NOT NULL,
  email       TEXT UNIQUE NOT NULL,
  phone       TEXT,
  password    TEXT NOT NULL,             -- bcrypt hash
  created_at  INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE plans (
  id              TEXT PRIMARY KEY,      -- UUID
  name            TEXT NOT NULL,         -- "Lite 90", "Standard 180", "Premium 365"
  term_days       INTEGER NOT NULL,     -- 90, 180, 365
  total_amount    INTEGER NOT NULL,     -- in cents: 1200000, 2400000, 4500000
  daily_rate      INTEGER NOT NULL,     -- in cents: 13333, 13333, 12329
  min_down_payment INTEGER NOT NULL,    -- in cents
  created_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE devices (
  id              TEXT PRIMARY KEY,      -- UUID
  imei            TEXT UNIQUE NOT NULL,  -- 15-digit IMEI
  model           TEXT NOT NULL,         -- "Tecno Spark 20", "Samsung A05s", etc.
  dealer_id       TEXT NOT NULL REFERENCES dealers(id),
  status          TEXT NOT NULL DEFAULT 'in_stock',  -- in_stock, sold, recalled
  created_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE accounts (
  id                      TEXT PRIMARY KEY,      -- e.g. "ACC-100245"
  customer_name           TEXT NOT NULL,
  national_id             TEXT NOT NULL,
  phone_number            TEXT NOT NULL,
  device_id               TEXT UNIQUE NOT NULL REFERENCES devices(id),
  dealer_id               TEXT NOT NULL REFERENCES dealers(id),
  plan_id                 TEXT NOT NULL REFERENCES plans(id),
  total_loan_amount       INTEGER NOT NULL,      -- cents
  amount_paid             INTEGER NOT NULL DEFAULT 0,  -- cents
  daily_rate              INTEGER NOT NULL,       -- cents
  next_payment_due        INTEGER NOT NULL,       -- epoch millis
  status                  TEXT NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, WARNING, LOCKED
  locked_by_dealer        INTEGER NOT NULL DEFAULT 0,     -- 1 = dealer forced lock
  down_payment            INTEGER NOT NULL,       -- cents
  term_days               INTEGER NOT NULL,
  currency_code           TEXT NOT NULL DEFAULT 'KES',
  created_at              INTEGER NOT NULL DEFAULT (unixepoch()),
  updated_at              INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE payments (
  id              TEXT PRIMARY KEY,      -- UUID
  account_id      TEXT NOT NULL REFERENCES accounts(id),
  amount          INTEGER NOT NULL,      -- cents
  method          TEXT NOT NULL,         -- 'mpesa', 'cash', 'bank_transfer'
  reference       TEXT,                  -- M-Pesa receipt, etc.
  recorded_by    TEXT NOT NULL,         -- dealer_id or 'system'
  created_at     INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE lock_events (
  id              TEXT PRIMARY KEY,      -- UUID
  account_id      TEXT NOT NULL REFERENCES accounts(id),
  event_type      TEXT NOT NULL,         -- 'lock', 'unlock', 'grace_start', 'grace_end'
  triggered_by    TEXT NOT NULL,         -- 'system', 'dealer', 'customer'
  created_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE TABLE sessions (
  id              TEXT PRIMARY KEY,      -- UUID
  dealer_id       TEXT NOT NULL REFERENCES dealers(id),
  token           TEXT UNIQUE NOT NULL,
  expires_at      INTEGER NOT NULL,
  created_at      INTEGER NOT NULL DEFAULT (unixepoch())
);

-- Indexes for common queries
CREATE INDEX idx_accounts_dealer ON accounts(dealer_id);
CREATE INDEX idx_accounts_status ON accounts(status);
CREATE INDEX idx_accounts_phone  ON accounts(phone_number);
CREATE INDEX idx_accounts_due    ON accounts(next_payment_due);
CREATE INDEX idx_payments_account ON payments(account_id);
CREATE INDEX idx_payments_created ON payments(created_at);
CREATE INDEX idx_devices_imei    ON devices(imei);
CREATE INDEX idx_devices_dealer  ON devices(dealer_id);
CREATE INDEX idx_lock_events_account ON lock_events(account_id);
```

## API Endpoints (SvelteKit API Routes)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/login` | None | Dealer login, returns JWT |
| POST | `/api/auth/logout` | Dealer | Invalidate session |
| GET | `/api/accounts` | Dealer | List all accounts for dealer |
| GET | `/api/accounts/:id` | Dealer | Get single account |
| POST | `/api/accounts` | Dealer | Create new account (enrollment) |
| PATCH | `/api/accounts/:id` | Dealer | Update account (extend timer, etc.) |
| POST | `/api/accounts/:id/force-lock` | Dealer | Dealer-initiated device lock |
| POST | `/api/accounts/:id/force-unlock` | Dealer | Dealer-initiated device unlock |
| GET | `/api/accounts/:id/payments` | Dealer | Payment history for account |
| POST | `/api/payments` | Dealer | Record a payment |
| GET | `/api/devices` | Dealer | List devices (inventory) |
| POST | `/api/devices` | Dealer | Add device to inventory |
| GET | `/api/kpis` | Dealer | Dashboard KPI aggregates |
| GET | `/api/ledger` | Dealer | Payment ledger with filters |
| GET | `/api/plans` | Dealer | List available plans |
| GET | `/api/device/check` | Device | Check if IMEI is enrolled + status |
| POST | `/api/device/heartbeat` | Device | Daily check-in from customer app |

## Status Derivation Rule (CRITICAL — shared across all 3 apps)

```
now = current epoch milliseconds
if (dealer forced lock)           → LOCKED
else if (now >= nextPaymentDue)   → LOCKED
else if (now >= nextPaymentDue - 24h) → WARNING
else                              → ACTIVE
```

This rule MUST stay identical in:
- `customer-app/.../DeviceStatus.kt`
- `dealer-dashboard/src/lib/api/mockApi.ts` (→ `computeStatus()`)
- `agent-app/.../EnrollmentStatus.kt`
- Backend API (new)

## Tech Stack

| Layer | Technology | Notes |
|-------|-----------|-------|
| Customer App | Kotlin, Jetpack Compose, M3 | Min SDK 26, Target SDK 34 |
| Agent App | Kotlin, Jetpack Compose, M3 | Min SDK 26, Target SDK 34 |
| Dealer Dashboard | SvelteKit 2, TypeScript, TailwindCSS 3 | SPA mode, no SSR |
| Backend API | SvelteKit API routes | Same process as dashboard |
| Database | Turso (libSQL) | Free tier: 9 GB storage, 1B row reads/mo |
| Auth | JWT + bcrypt | Dealer login sessions |
| Build | Vite 5 (dashboard), Gradle 8.7 (Android) | |
| CI/CD | GitHub Actions | Build + sign both APKs on push to main |

## Turso Setup (Manual Steps Required)

You said you've already logged in to Turso. Here's what needs to happen:

### 1. Install the Turso CLI
```bash
curl -sSfL https://get.tur.so/install.sh | bash
```

### 2. Create the database
```bash
turso db create securepay
```

### 3. Get the database URL
```bash
turso db show securepay --url
# Should output something like: libsql://securepay-<your-org>.turso.io
```

### 4. Create an auth token
```bash
turso db tokens create securepay
# Copy this token — it goes in your .env as TURSO_AUTH_TOKEN
```

### 5. Apply the schema
```bash
turso db shell securepay < schema.sql
```
(We'll generate `schema.sql` from the CREATE TABLE statements above)

### 6. Turso Free Tier Limits
- 9 GB storage
- 1 billion row reads/month
- 25 million row writes/month
- 500 databases
- 3 databases with embedded replicas
- More than enough for a production app with thousands of customers

## Project Phases

### Phase 1: Backend + Database (COMPLETE)
- [x] Create Turso database + apply schema
- [x] Add `@libsql/client` to dealer-dashboard
- [x] Create SvelteKit API routes (`/api/*`)
- [x] Implement JWT auth for dealer login
- [x] Replace `mockApi.ts` with real Turso queries
- [x] Seed plans table (Lite 90, Standard 180, Premium 365)
- [x] Seed a demo dealer account

### Phase 2: Device Lock System (Android DPC)
- [ ] Implement Device Owner provisioning via NFC/QR (Android 6+) or factory reset flow
- [ ] Make customer-app survive factory reset (Device Owner persists through FRP)
- [ ] Implement FRP-triggered auto-install: app launches on FRP completion
- [ ] Customer must authenticate (phone + OTP) to activate device after FRP
- [ ] Implement daily heartbeat: app polls `/api/device/check` every 1-4 hours
- [ ] Implement local lock/unlock: app checks `nextPaymentDue` locally + server
- [ ] If payment overdue → full-screen LockOverlay, restrict device via DPC policies
- [ ] If payment made → server updates `nextPaymentDue`, app unlocks on next heartbeat

### Phase 3: Agent App Real Integration
- [ ] Replace `MockEnrollmentRepository` with real API calls
- [ ] Agent login (same dealer auth)
- [ ] Real IMEI scan → check `/api/device/check` to verify device is in inventory
- [ ] Submit enrollment to `/api/accounts` endpoint
- [ ] Show real-time account creation confirmation

### Phase 4: Payment Integration
- [ ] M-Pesa STK Push / C2B integration (Daraja API)
- [ ] Manual payment recording (cash at dealer shop)
- [ ] Payment confirmation webhook from M-Pesa
- [ ] Auto-extend `nextPaymentDue` on successful payment
- [ ] Payment reminders (SMS via Africa's Talking or similar)

### Phase 5: Production Hardening
- [ ] HTTPS everywhere (Turso provides this)
- [ ] API rate limiting
- [ ] Device token verification (HMAC signing of heartbeat requests)
- [ ] Encrypted local storage on device (Android EncryptedSharedPreferences)
- [ ] Anti-tamper: detect root, detect app uninstall attempt
- [ ] Remote config: dealer can change lock policies from dashboard
- [ ] Audit logging (all lock/unlock/payment events)
- [ ] Backup & disaster recovery plan for Turso

## Key Decisions

- **No new npm dependencies for charts** — all charts are pure SVG (Donut, ProgressRing, Sparkline, BarChart, StackedBar, AreaChart, Gauge)
- **Single SvelteKit process** serves both the dashboard UI and the API routes — no separate backend server needed
- **Turso (libSQL)** for database — SQLite-compatible, serverless, free tier is generous
- **JWT auth** for dealer sessions — simple, stateless, works with mobile apps too
- **Device Owner mode** for customer-app — survives factory reset, can enforce device policies
- **Daily payment model** — `nextPaymentDue` is always `lastPaymentDate + 24 hours`; each day the customer must pay the `dailyRate` to keep the device unlocked
- **Heartbeat model** — customer app checks in every 1-4 hours; if server says LOCKED, device locks; if offline for >24h past deadline, device locks based on local clock
- **Currency in cents** — all monetary values stored as integers (cents) to avoid floating-point issues; formatted to KES on display

## File Conventions

- **Types** are defined once in `dealer-dashboard/src/lib/types.ts` and mirrored in Kotlin data classes
- **Status rule** must be identical across all codebases (see above)
- **Mock APIs** are being replaced — `mockApi.ts` and `MockSecurePayApi.kt` are transitional
- **Colors**: Charcoal `#121212`, Surface `#1E1E1E`, Emerald `#10B981`, Amber `#F59E0B`, Crimson `#DC2626`
- **Svelte components** go in `src/lib/components/`; charts in `src/lib/components/charts/`
- **Kotlin packages** use `com.securepay.customer` and `com.securepay.agent`
- **No comments in code** unless explicitly requested
- **API routes** go in `src/routes/api/*/+server.ts` — each REST endpoint is a SvelteKit server route
- **Auth middleware** is in `src/hooks.server.ts` — validates JWT from `Authorization: Bearer` header
- **Database access** uses `src/lib/db.ts` (Turso client) — all queries use parameterized SQL
- **Client API** is in `src/lib/api/client.ts` — fetch wrapper with auth token management
- **Seed endpoint** is `POST /api/seed` — populates demo data (devices, accounts, payments)

## Demo Credentials

- **Email**: `dealer@securepay.io`
- **Password**: `dealer123`
- **Seed**: `POST /api/seed` (after login, call this to populate demo data)

## Dev Commands

```bash
# Dealer Dashboard
cd dealer-dashboard
npm install
npm run dev          # Starts on localhost:5173
npm run build        # Production build
npm run check        # TypeScript + Svelte checking

# Customer App
cd customer-app
./gradlew :app:assembleDebug      # Debug APK
./gradlew :app:assembleRelease    # Release APK

# Agent App
cd agent-app
./gradlew :app:assembleDebug      # Debug APK
./gradlew :app:assembleRelease    # Release APK
```

## Environment Variables

Create `dealer-dashboard/.env`:
```
TURSO_CONNECTION_URL=libsql://securepay-<your-org>.turso.io
TURSO_AUTH_TOKEN=<token-from-turso-db-tokens-create>
JWT_SECRET=<random-32-char-string>
```

## Turso Free Tier Reference

| Resource | Limit |
|----------|-------|
| Storage | 9 GB |
| Row reads | 1 billion/month |
| Row writes | 25 million/month |
| Databases | 500 |
| Embedded replicas | 3 |

That's ~27,000 customers × 365 days of daily payments = ~10M rows/year, well within free tier.