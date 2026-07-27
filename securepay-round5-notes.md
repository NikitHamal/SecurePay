# Round 5 — session expiry + admin-set pricing

Branch: `arena/019fa43e-securepay` (pushed) · commit `a32bbea` on top of `c373881`
Agent app: **1.5.1 (11)** · Customer app: unchanged 1.6.0 (27) · Dashboard: no migration needed

---

## 1. "Unauthorized" no longer sticks on the screen

**Client:** *"When this happens let the app Automatically Logs out and move to the Sign in page. Else, this will be on the screen all day unless I go to settings and logout then login again."*

**What was wrong:** `AuthInterceptor` explicitly kept the dead token on a 401 ("Do NOT auto-clear the session on 401"), so every screen just kept rendering `Unauthorized` forever.

**Fix:**
- New `data/remote/SessionEvents.kt` — a tiny app-wide `SharedFlow` for session signals.
- `AuthInterceptor` — on **HTTP 401 from any authenticated endpoint** it now clears the encrypted session and emits `sessionExpired`.
- `SecurePayNavHost` — listens for that signal and immediately navigates to **Sign in** (`popUpTo(0)`, whole back stack wiped) with a toast: *"Your session expired. Please sign in again."*

Guardrails:
- `/auth/login` and `/auth/register` are excluded — a wrong password still shows its own inline error and does **not** trigger a logout loop.
- Only fires when a token actually existed, so an unauthenticated app can't bounce itself.

Files: `data/remote/SessionEvents.kt` (new), `data/remote/AuthInterceptor.kt`, `ui/navigation/SecurePayNavHost.kt`.

---

## 2. Pricing is now 100% admin-set (that GHS 6,000 error is gone)

**Client:** *"Also, can we have it only to custom so that Admin can set the prices they want. Because that Down payment 6000 is even more than that if the phone 2times."*

**What was wrong:** the seeded packages (`Lite 90`, `Standard 180`, `Premium 365`) carried `min_down_payment` of 300000/600000/1000000 pesewas. Picking `Standard 180` forced a **GHS 6,000** deposit floor, and both the app and the API rejected anything below it — with a raw-pesewa error message on top.

**Fix — agent app:**
- The **Offers** step is now *"Set the price for this sale"*: three fields — **Total price (GH₵)**, **Repayment period (days)**, **Daily repayment rate (GH₵)**. No package cards at all.
- A **suggested daily rate** is computed as `(total − deposit) ÷ days`, rounded up to the pesewa, with a one-tap **Use** button. Purely a hint — type anything you like.
- A live "Your offer" card renders the M-KOPA style summary (lock rows, `N DAYS` pill, bold total) from *your* numbers.
- **Loan details** step: the deposit accepts **anything from 0 up to the total price**. No minimum. Helper text says exactly that.
- Removed all plan plumbing (`loadPlans`, `selectPlan`, `selectCustomPlan`, `selectedPlan`, `availablePlans`) and deleted the 5 dead legacy step files.

**Fix — API (`POST /api/accounts`):**
- The plan minimum is now only a *default* when no deposit is sent; it is **never enforced**. `parseSafeInteger(body.downPayment, 'downPayment', 0)`.
- Money errors now read in cedis, e.g. `downPayment must be at least GHS 0.00`, and `Initial payment GHS 6,000.00 cannot be more than the total price GHS 2,277.80` — no more bare pesewa integers in the UI.

**Fix — dashboard New Loan wizard:** identical treatment. Plans dropped, custom pricing grid + suggested daily rate + live offer summary, deposit hint reads *"Any amount from 0 up to the total price GH₵ X"*.

Files: `ui/enrollment/{EnrollmentUiState,EnrollmentViewModel,EnrollmentWizardScreen}.kt`, `ui/enrollment/steps/ProductStep.kt`, deleted `steps/{PlanStep,ReviewStep,KycStep,ScannerStep,SignerStep}.kt`, `app/build.gradle.kts`, `src/routes/api/accounts/+server.ts`, `src/lib/components/NewLoanModal.svelte`.

> Existing accounts and the `plans` table are untouched — old loans still display their plan name. Nothing to migrate.

---

## 3. Bonus: the codebase is now clean

You asked about the 12 leftover errors. All fixed, plus the 11 warnings:

| Was | Fix |
|---|---|
| `api/ads/[id]/+server.ts` × 11 — `'updated' is possibly 'null'` | added a `if (!updated) return errorResponse('Ad not found', 404)` guard after the re-read |
| `api/agent/app-update/+server.ts` — `AGENT_APP_MIN_SUPPORTED_VERSION_CODE` missing on `Platform.env` | declared it in `src/app.d.ts` next to the customer-app one |
| 11 × a11y `A form label must be associated with a control` (PayWithMoMoModal, admin/push, ads) | added `for`/`id` pairs to real inputs; group headings (Network, Push Type, Status, Account ID) changed from `<label>` to `<span>` |

```
svelte-check: 0 errors and 0 warnings
ktcheck:      clean (SecurePayRepository.kt is the long-standing false positive, untouched)
```

---

## Deploy

```bash
# dashboard — no new migration this round
cd dealer-dashboard
npm install
npm run build
npx wrangler pages deploy .svelte-kit/cloudflare

# agent app
cd agent-app
./gradlew assembleRelease     # 1.5.1 (11)
```

Nothing to run against D1. The customer app is unchanged.
