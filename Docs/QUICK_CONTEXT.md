# SecurePay — Quick Context Summary
### What You Need to Know Right Now

---

## The App (Current State)

Your SecurePay app has 3 parts:
1. **Dealer Dashboard** (web app) — SvelteKit on Cloudflare Workers/D1/R2
2. **Agent App** (Android) — Kotlin/Compose for enrolling customers
3. **Customer App** (Android) — Kotlin/Compose with Device Owner for locking phones

**What works:** Device locking, KYC enrollment, payment tracking, stolen device tracking, FCM push, CI/CD.

**What's missing for the client's actual needs:** Multi-tenant hierarchy, agent isolation, agent self-registration, Ghana Card verification (Didit), notifications.

---

## What the Client Wants (In Simple Terms)

1. **Multiple agents** selling phones for him
2. **Agent A can't see Agent B's sales** (data isolation)
3. **Client (Super Admin) sees EVERYTHING**
4. **Agents register themselves**, client approves them
5. **Client gets notified** when an agent makes a sale
6. **Ghana Card verification** via Didit.me API

---

## What I've Created for You

| File | What It Is |
|------|-----------|
| `PRODUCTION_AUDIT_AND_ROADMAP.md` | Complete technical audit — every bug, every gap, every recommendation |
| `MIGRATION_0002_MULTI_TENANT.sql` | Database migration to add roles, agencies, branches, notifications |
| `IMPLEMENTATION_REFERENCE.md` | Ready-to-use API endpoint code for all new multi-tenant features |
| `48_HOUR_SPRINT.md` | Hour-by-hour action plan to get everything done before the deadline |
| `QUICK_CONTEXT.md` | This file — summary for quick reference |

---

## Didit.me — What You Need to Know

- **Free tier:** 500 verifications/month, forever, no credit card
- **Cost after:** $0.33 per verification
- **What it does:** Scans Ghana Card → checks liveness → matches face → returns "Approved" or "Declined"
- **Integration time:** ~2 hours once you have the API key
- **Setup:** Sign up at business.didit.me → create workflow → add secrets to Cloudflare

**The flow:** Agent captures customer's Ghana Card → your backend calls Didit → customer gets a verification link → customer verifies on their phone → Didit webhook tells your server the result → account marked as verified.

---

## The Multi-Tenant Hierarchy

```
TB (Client) — Super Admin — sees EVERYTHING
    │
    ├── DSL Agency 1 — Agency Owner — sees own agency
    │   ├── Branch A — Branch Admin — sees own branch
    │   │   ├── Agent 1 — sees only own customers
    │   │   └── Agent 2 — sees only own customers
    │   └── Branch B
    │       └── Agent 3
    │
    └── DSL Agency 2
        └── ...
```

**Data isolation rule:** Each user's queries are filtered by their role:
- Super Admin: `WHERE 1=1` (no filter)
- Agency Owner: `WHERE agency_id = ?`
- Branch Admin: `WHERE branch_id = ?`
- Agent: `WHERE enrolled_by = ?`

---

## The 48-Hour Plan

| Time | What to Do |
|------|-----------|
| Hour 0-2 | Fix critical bugs (account IDs, fs import, photo limits) |
| Hour 2-8 | Run database migration, add role system |
| Hour 8-16 | Build new API endpoints (register-agent, approve, notifications) |
| Hour 16-24 | Update agent app (self-registration, role-aware UI) |
| Hour 24-36 | Dashboard UI (agent management, requests page, notification bell) |
| Hour 36-44 | Didit integration (sign up, create workflow, webhook handler) |
| Hour 44-48 | Test, deploy, build APKs, prepare demo |

---

## Provisioning — What to Tell the Client

> "The provisioning system works. We're using ADB to set up devices, which is the standard approach for pilot programs. M-KOPA used the same method when they started. As we scale, we'll move to Google Play Store distribution and Samsung Knox Mobile Enrollment, but for the first 50-100 devices, ADB is the right approach."

---

## Files to Read (In Order)

1. `48_HOUR_SPRINT.md` — Start here, this is your action plan
2. `PRODUCTION_AUDIT_AND_ROADMAP.md` — Full audit if you need details
3. `MIGRATION_0002_MULTI_TENANT.sql` — Run this against your database
4. `IMPLEMENTATION_REFERENCE.md` — Copy-paste API code

---

## One More Thing

The technical foundation is **genuinely strong** for a 1-week-old project. The DPC implementation, HMAC authentication, and offline tracking are all production-quality. What you need now is the **business logic layer** (multi-tenancy, agent management, KYC integration) — and that's additive work, not a rewrite.

You've got this. Focus on the demo. Nail the three things the client cares about:
1. Agent isolation (agents can't see each other's data)
2. Admin sees everything
3. Device locking works

Everything else can wait.
