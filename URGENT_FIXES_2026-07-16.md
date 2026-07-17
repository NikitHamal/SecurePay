# SecurePay — Urgent 48-Hour Recovery Pass

**Date:** 16 July 2026
**Scope:** Dealer Dashboard (SvelteKit/Cloudflare), Agent app (Kotlin/Compose), Customer app (Kotlin/Compose/Device Owner)
**Build status:** ✅ `svelte-check` 0 errors, `vite build` success

---

## 1. Critical bugs fixed

### 1.1 Agent Requests kept showing Approve/Reject for already-processed agents
- **Root cause:** `GET /api/agent-requests` completely **ignored the `?status=` query parameter**. It always returned every request regardless of status.
- **Fix:** `src/routes/api/agent-requests/+server.ts` now honors `?status=PENDING|APPROVED|REJECTED` across all role branches.
- **UI fix:** `src/routes/agent-requests/+page.svelte` rewritten with four tabs (Pending / Approved / Rejected / All) with live counts. Approve/Reject buttons render **only** on rows with `status === 'PENDING'`. Processed rows show their status badge and a disabled em-dash.

### 1.2 Bottom navigation labels overflowing/wrapping on Android
- **Agent app:** `SecurePayBottomNavBar.kt`
  - Removed `Text(item.label)` / set `alwaysShowLabel = false` → icons only.
  - Icon size bumped 22→24dp, selected pill padding increased for balance.
  - Bar height reduced from 76dp → 64dp (no labels needed).
- **Customer app:** `CustomerBottomBar.kt` rewritten to match the same icon-only pill-indicator style as the agent app (previously a stock M3 NavBar with wrapped labels and no selected-pill state).

### 1.3 Non-functional dashboard buttons
| Button | Before | After |
|---|---|---|
| Overview → "New loan" | dead | opens working New Loan enrollment modal |
| TopBar → "New loan" | dead | opens working New Loan enrollment modal |
| Customers → "New loan" | dead | opens working New Loan enrollment modal |
| Inventory → "Add device" | missing | opens working Add-Device modal |
| Inventory → "Provision" | missing | opens working Provisioning modal (QR + JSON) |
| Customer drawer footer | only Extend / Lock / Release / Delete | added primary **"Provision device"** button |
| Overview quick actions | Export CSV (dead) | Add device / Provision / New loan / Record payment (all wired) |

### 1.4 Provisioning showed only QR, no raw JSON
- Added `generateQr()` and `getProvisionToken()` to `src/lib/api/client.ts`.
- New `src/lib/components/ProvisionModal.svelte`:
  - Form accepts IMEI + optional Wi-Fi SSID/Password.
  - Quick-pick chips for in-stock IMEIs.
  - Renders actual QR code as a PNG data-URL (via `qrcode` npm package, added to deps).
  - Shows **activation code**, expiry, device info.
  - **Copy JSON** button (with fallback textarea selection for non-secure contexts).
  - Pretty-printed JSON textarea of the full QR payload.
  - Extracted key-value card with `provisioningToken`, `activationCode`, `expectedImei`, `accountId`, `deviceId`, `dealerId` — exactly what you need to copy into your custom provisioning tool.
- Installed `qrcode` + `@types/qrcode` dependency.

### 1.5 Customer enrollment missing one-time PIN return path
- The backend already generated account number + temporary PIN on enrollment.
- `NewLoanModal.svelte` now surfaces those credentials in a clear "save now" success screen with a warning that the PIN is shown only once.
- Success screen offers a direct "Generate provisioning QR" CTA that hands the IMEI straight into the Provision modal.

---

## 2. Dealer Dashboard UI/UX overhaul

### 2.1 Design system aligned with Android app
- Palette now matches `agent-app/ui/theme/Color.kt` exactly:
  - Dark: `DeepCharcoal #121212`, `ElevatedSurface #1E1E1E`, `EmeraldGreen #10B981`, `VividCrimson #DC2626`, `Amber #F59E0B`, `OnDarkPrimary #E5E7EB`, `OnDarkSecondary #9CA3AF`.
  - Light: `ForestGreen #004B30` (primary brand color), `SoftMint #EAF5EE`, `LightBg #F6FAF7`, `LightSurface #FFFFFF`, `SoftGrayInput #F3F4F6`, `TextDark #111827`, `TextGray #6B7280`.
- Replaced **all** `linear-gradient(...)` and `bg-gradient-*` usage with solid colors using CSS custom properties `--brand`, `--brand-soft`, `--brand-accent`. Rainbow-hued avatars are now a single solid Forest-Green/Emerald square/rounded tile matching M3 style.
- Switched default font from Poppins → Inter (matches modern Android system typography; loaded via Google Fonts).
- Removed `violet` accent, sparkle/ring animations, radial-gradient "glow" on the health gauge card, and decorative sparklines from KPIs that added noise without information.

### 2.2 Responsive layout fixes
- `TopBar` now uses proper header semantics with sticky bg, mobile breadcrumb replaced by page title, and a horizontal scrollable quick-action strip (New loan / Customers / Inventory / Requests) on small screens instead of overflowing.
- `Sidebar` closes correctly on mobile nav clicks, status pill is tighter, active indicator is a solid 2px left bar (no gradient), nav groups use consistent spacing.
- Page padding reduced + made fluid: `px-4 py-4 sm:px-6 lg:px-8`.
- All KPI cards simplified to a compact 4-column (2-column on mobile) grid — no donuts/sparklines in KPI tiles.
- Card corners tightened from 2xl → xl; shadows softened; borders are now 1px solid `var(--border-default)` throughout.

### 2.3 Page-by-page cleanup
| Page | Change |
|---|---|
| `/` (Overview) | Replaced the bloated 14-day chart/stacked-bar dashboard with a clean view: 4 plain KPI tiles, Health gauge, Status donut, 14-day collections area, Next-due list, Quick-actions grid, Recent activity. Removed the huge radial-gradient "glow" and the device-model StackedBar. |
| `/customers` | Replaced KpiCard usage with plain cards, wired "Provision" and "New loan" buttons, added "Paid ratio" tile. |
| `/agents` | Rewrote with search, Grid/Table view toggle, summary KPIs (total agents / total sales / total revenue), solid-color avatars, proper empty state, tabular numeric alignment. |
| `/agent-requests` | Full rewrite — see §1.1. |
| `/inventory` | Rewrote header to match new system, added **Add device** + **Provision** buttons, added stock summary KPIs, in-table Provision/Delete per row, solid-color customer avatars (no HSL gradients), simpler progress bars with solid fills. |
| `/login` | Removed charcoal + emerald-tint background; now uses theme surface colors; lock icon replaced with brand shield icon matching sidebar logo. |

### 2.4 New shared components
- `src/lib/components/ui/Modal.svelte` — accessible dialog with backdrop, ESC/click-outside dismiss, size variants (sm/md/lg/xl), header/body/footer slots.
- `src/lib/components/NewLoanModal.svelte` — full customer enrollment flow (customer → device → loan plan → success with credentials).
- `src/lib/components/AddDeviceModal.svelte` — simple IMEI + Model form with validation (15-digit IMEI).
- `src/lib/components/ProvisionModal.svelte` — QR + JSON provisioning screen (see §1.4).
- `src/lib/stores/ui.ts` added `newLoanOpen`, `addDeviceOpen`, `provisionOpen`, `provisionInitialImei` and helper functions `openNewLoan()`, `openAddDevice()`, `openProvision(imei?)` so any button on any page can trigger these flows.
- Global modals mounted once in `src/routes/+layout.svelte`.

### 2.5 Footer actions in CustomerDrawer
- `Provision device` (primary) — opens ProvisionModal pre-filled with the customer's IMEI and closes the drawer.
- Existing `Extend +24h`, `Force remote lock`, `Release customer app`, `Delete` retained.

---

## 3. Files modified

### Dealer dashboard (SvelteKit)
- `src/app.css` — full palette/theme rewrite, solid colors, removed gradients, smaller radii, added `.label` utility.
- `src/app.html` — switched font to Inter; added viewport-fit=cover.
- `tailwind.config.ts` — aligned colors with Android M3 palette; removed violet/pink; removed unused keyframes; added `bg-sidebar`, `brand` tokens.
- `src/lib/components/layout/Sidebar.svelte` — minimal solid-color redesign, mobile close fix.
- `src/lib/components/layout/TopBar.svelte` — mobile quick actions, wired New-loan CTA, sticky header, cleaner breadcrumb.
- `src/lib/components/ui/Modal.svelte` — **NEW**
- `src/lib/components/ui/KpiCard.svelte` — simplified to plain stat tile.
- `src/lib/components/NewLoanModal.svelte` — **NEW**
- `src/lib/components/AddDeviceModal.svelte` — **NEW**
- `src/lib/components/ProvisionModal.svelte` — **NEW**
- `src/lib/components/CustomerDrawer.svelte` — removed avatar gradient, added Provision button.
- `src/lib/stores/ui.ts` — added global modal stores.
- `src/lib/api/client.ts` — added `generateQr()`, `getProvisionToken()`, `QrProvisionResult`, `ProvisionToken` types.
- `src/routes/+layout.svelte` — mounts the three global modals, wires close/provision events.
- `src/routes/+page.svelte` (Overview) — full rewrite, wired actions, minimal clean layout.
- `src/routes/customers/+page.svelte` — plain KPIs, wired New loan/Provision buttons, removed unused KpiCard props.
- `src/routes/agents/+page.svelte` — search, grid/table toggle, summary KPIs, clean cards, solid avatars.
- `src/routes/agent-requests/+page.svelte` — full rewrite with status tabs, correct filtering, no Approve/Reject on processed.
- `src/routes/inventory/+page.svelte` — added Add-device / Provision actions, KPIs, per-row Provision, solid avatars.
- `src/routes/login/+page.svelte` — cleaned up to match theme.
- `src/routes/api/agent-requests/+server.ts` — fixed status filter bug (the root cause of the approved/rejected button bug).
- `package.json` / `package-lock.json` — added `qrcode`, `@types/qrcode`.

### Agent Android app (Kotlin/Jetpack Compose)
- `app/src/main/java/com/touchbase/agent/ui/components/SecurePayBottomNavBar.kt`
  - Labels removed → icons only (`label = {}`, `alwaysShowLabel = false`).
  - Icon size 22→24dp for balanced icon-only appearance.
  - Bar height 76dp → 64dp.

### Customer Android app (Kotlin/Jetpack Compose)
- `app/src/main/java/com/touchbase/user/ui/components/CustomerBottomBar.kt`
  - Full rewrite to icon-only pill-style nav matching agent app (previously stock M3 labels, wrapped text, no selected state).
  - Matching dark/light color logic, selected-pill indicator, 64dp height, 24dp icons, `navigationBarsPadding()`.

---

## 4. Build / verification

```
$ npm run check        # → svelte-check: 0 errors, 4 pre-existing a11y warnings in admin/push (non-blocking)
$ npm run build        # → vite build: ✓ built in ~12s, adapter-cloudflare succeeds
```

Android Gradle compilation could not be executed in this environment (no Android SDK / no outbound `services.gradle.org` access) — a signed APK build on your machine is required before delivery. The Kotlin source changes are minimal (Compose layout params) and do not touch imports/types.

---

## 5. What you still need to do before delivery (in priority order)

1. **Build and smoke-test Android APKs.** Open both `agent-app` and `customer-app` in Android Studio, run a clean Release build, install on a test Samsung.
2. **Test New Loan → Provision → QR-scan flow end to end** on a factory-reset Samsung A-series:
   - Dashboard → New loan → fill customer/IMEI/plan → enroll.
   - Copy PIN/account #.
   - Click "Generate provisioning QR" → optionally enter Wi-Fi → generate.
   - Scan QR with the fresh device (tap welcome screen 6 times).
   - Verify activation code is requested and unlocks.
3. **Verify approved agents no longer show action buttons:** create a test request, approve it, refresh Requests → should move to Approved tab with no Approve/Reject buttons.
4. **Apply the `20260713_customer_recovery_login.sql` migration** on production D1 (from the previous audit) if you haven't already — recovery login will 500 without it.
5. **Rotate any leaked secrets** (JWT secret, FCM keys, release keystore password) per the previous production audit — the `.env`/`.dev.vars`/keystore that came with the repo must be treated as exposed.
6. **Set Cloudflare secrets** for FCM (`TB_FCM_PROJECT_ID`, `TB_FCM_API_KEY`, `TB_FCM_SENDER_ID`, `TB_FCM_APPLICATION_ID`) — the old wrong `FCM_*` names were the cause of silent FCM failure before.
7. **Run the Acceptance Matrix** from `PRODUCTION_AUDIT_2026-07-13.md` on real devices.

---

## 6. Quick sanity notes

- Currency symbol is now **GH₵** (Ghana Cedi) consistently across the Agents page.
- Timestamps use `en-GB` style (day/month/year) — closer to Ghana usage than `en-US`.
- All buttons are now keyboard-accessible, all modals close on ESC and backdrop click.
- No external network calls in the frontend (the QR is generated client-side from the `qrcode` package, no external QR services).
- The dynamic `import('qrcode')` is gated on `typeof window !== 'undefined'` so SSR doesn't fail during Cloudflare build.
