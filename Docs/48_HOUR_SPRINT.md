# 🚀 48-HOUR SPRINT: SecurePay Multi-Tenant + Didit Integration
### For Nikit — Get This Done Before Client Deadline

---

## ⏰ PRIORITY ORDER (Do These First)

### 🔴 HOUR 0-2: Critical Bug Fixes

**1. Fix Account ID Collision Risk**
File: `dealer-dashboard/src/routes/api/accounts/+server.ts`
```typescript
// FIND THIS:
const accountId = `ACC-${100000 + Math.floor(Math.random() * 900000)}`;

// REPLACE WITH:
const accountId = `ACC-${crypto.randomUUID().replace(/-/g, '').slice(0, 12).toUpperCase()}`;
```

**2. Fix fs Import Crash in Production**
File: `dealer-dashboard/src/lib/api/server.ts`
```typescript
// The `import * as fs from 'fs'` at the top will crash in Cloudflare Workers.
// MOVE it inside the getR2() function and guard it:

export function getR2(event) {
  if (event.platform?.env?.R2) return event.platform.env.R2;

  // Dev-only mock (Node.js environment)
  if (typeof process !== 'undefined' && process.env?.NODE_ENV === 'development') {
    const fs = await import('fs');
    const path = await import('path');
    // ... existing mock code using fs and path
  }

  throw new Error('R2 bucket not available');
}
```

**3. Add Photo Size Limit**
File: `dealer-dashboard/src/routes/api/accounts/+server.ts`
```typescript
// At the top of POST handler, before base64 decode:
const MAX_PHOTO_SIZE = 5 * 1024 * 1024; // 5MB
for (const [key, val] of Object.entries({ customerPhoto, nationalIdFront, nationalIdBack })) {
  if (val && typeof val === 'string') {
    const sizeEstimate = (val.length * 3) / 4; // base64 is 4/3 larger than binary
    if (sizeEstimate > MAX_PHOTO_SIZE) {
      return errorResponse(`${key} exceeds 5MB limit`, 413);
    }
  }
}
```

**4. Verify Currency Code**
File: `dealer-dashboard/src/routes/api/accounts/+server.ts`
```typescript
// In POST handler, the insert statement should use 'GHS' not 'KES':
// FIND: 'KES'
// REPLACE: 'GHS'
// (The latest code already uses 'GHS' — just verify)
```

---

### 🟠 HOUR 2-8: Multi-Tenant Database Setup

**5. Run the Migration**
```bash
cd dealer-dashboard
npx wrangler d1 execute securepay-db --file=../../MIGRATION_0002_MULTI_TENANT.sql
```

If you get "duplicate column" errors, that's OK — just run each ALTER TABLE individually and skip the ones that fail.

**6. Update `hooks.server.ts` — Add Role to JWT Locals**
```typescript
// After the JWT verification block:
if (dealer) {
  event.locals.dealer = {
    id: dealer.sub,
    name: dealer.name,
    role: dealer.role || 'AGENT',
    agencyId: dealer.agencyId,
    branchId: dealer.branchId
  };
}
```

**7. Update `types.ts` — Add Role Types**
```typescript
// Add to dealer-dashboard/src/lib/types.ts:
export type DealerRole = 'SUPER_ADMIN' | 'AGENCY_OWNER' | 'BRANCH_ADMIN' | 'AGENT';

export interface DealerLocals {
  id: string;
  name: string;
  role: DealerRole;
  agencyId?: string;
  branchId?: string;
}
```

---

### 🟡 HOUR 8-16: API Endpoints for Multi-Tenant

**8. Create These New Route Files:**
```
dealer-dashboard/src/routes/
├── api/
│   ├── auth/
│   │   ├── register-agent/+server.ts    (from IMPLEMENTATION_REFERENCE.md)
│   │   ├── approve-agent/+server.ts
│   │   └── reject-agent/+server.ts
│   ├── agent-requests/+server.ts
│   ├── notifications/+server.ts
│   ├── my-sales/+server.ts
│   └── webhooks/
│       └── didit/+server.ts
```

**9. Update Existing `/api/accounts` GET Endpoint**
```typescript
// Add role-based filtering:
export const GET: RequestHandler = async ({ locals, url, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });

  // Build WHERE clause based on role
  let whereClause = '1=1';
  let params: any[] = [];

  switch (locals.dealer.role) {
    case 'SUPER_ADMIN':
      whereClause = '1=1';
      break;
    case 'AGENCY_OWNER':
      whereClause = 'a.agency_id = ?';
      params = [locals.dealer.agencyId];
      break;
    case 'BRANCH_ADMIN':
      whereClause = 'a.branch_id = ?';
      params = [locals.dealer.branchId];
      break;
    case 'AGENT':
      whereClause = 'a.enrolled_by = ?';
      params = [locals.dealer.id];
      break;
  }

  const result = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE ${whereClause}
    ORDER BY a.created_at DESC
  `).bind(...params).all();

  // ... rest of mapping logic stays the same
};
```

---

### 🟢 HOUR 16-24: Agent App Updates

**10. Agent App — Add Self-Registration Screen**
File: `agent-app/.../ui/screens/RegisterScreen.kt` (NEW)
```kotlin
// Simple screen with:
// - Full Name field
// - Email field
// - Phone field
// - Password field
// - "Register" button → POST /api/auth/register-agent
// - Shows "Registration submitted, waiting for admin approval" message
```

**11. Agent App — Login Flow Update**
```kotlin
// After successful login, the API should return:
// { token, name, role, agencyId, branchId }
// Store role in SharedPreferences
// Show different UI based on role
```

---

### 🔵 HOUR 24-36: Dashboard UI for Multi-Tenant

**12. Add Navigation Based on Role**
File: `dealer-dashboard/src/routes/+layout.svelte`
```svelte
<!-- Role-based navigation -->
{#if $dealer?.role === 'SUPER_ADMIN' || $dealer?.role === 'AGENCY_OWNER'}
  <a href="/agencies">Agencies</a>
{/if}
{#if $dealer?.role !== 'AGENT'}
  <a href="/branches">Branches</a>
{/if}
{#if $dealer?.role === 'SUPER_ADMIN' || $dealer?.role === 'AGENCY_OWNER' || $dealer?.role === 'BRANCH_ADMIN'}
  <a href="/agents">Agents</a>
  <a href="/agent-requests">Agent Requests</a>
{/if}
<a href="/customers">Customers</a>
<a href="/devices">Devices</a>
<a href="/ledger">Ledger</a>
```

**13. Create Agent Management Page**
File: `dealer-dashboard/src/routes/agents/+page.svelte`
```svelte
<!-- Shows: -->
<!-- - List of agents in scope (branch/agency/all) -->
<!-- - Each agent card: name, email, phone, sales count, revenue -->
<!-- - Agent A's card shows only Agent A's stats -->
<!-- - Branch Admin sees all agents in their branch -->
```

**14. Create Agent Requests Page**
File: `dealer-dashboard/src/routes/agent-requests/+page.svelte`
```svelte
<!-- Shows pending registration requests -->
<!-- Each request: name, email, phone, date -->
<!-- Buttons: Approve / Reject -->
```

**15. Add Notification Bell**
File: `dealer-dashboard/src/routes/+layout.svelte`
```svelte
<!-- Top-right bell icon with unread count -->
<!-- Poll GET /api/notifications every 30 seconds -->
<!-- Show dropdown with recent notifications -->
```

---

### 🟣 HOUR 36-44: Didit Integration

**16. Set Up Didit Account**
1. Go to https://business.didit.me → Sign up (no credit card needed)
2. Get your API key from the dashboard
3. Create a workflow:
   ```bash
   curl -X POST https://verification.didit.me/v3/workflows/ \
     -H "x-api-key: YOUR_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"workflow_label": "Ghana Card KYC", "features": [{"feature": "ID_VERIFICATION"}, {"feature": "LIVENESS"}, {"feature": "FACE_MATCH"}, {"feature": "IP_ANALYSIS"}]}'
   ```
4. Save the `workflow_id`
5. Register webhook:
   ```bash
   curl -X POST https://verification.didit.me/v3/webhook/destinations/ \
     -H "x-api-key: YOUR_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"url": "https://securepay-dashboard.pages.dev/api/webhooks/didit", "subscribed_events": ["session.verified", "session.declined"]}'
   ```
6. Add to Cloudflare secrets:
   ```bash
   npx wrangler secret put DIDIT_API_KEY
   npx wrangler secret put DIDIT_WEBHOOK_SECRET
   npx wrangler secret put DIDIT_WORKFLOW_ID
   ```

**17. Add "Verify Ghana Card" Button to Dashboard**
File: `dealer-dashboard/src/routes/customers/[id]/+page.svelte`
```svelte
<!-- Add button that calls POST /api/accounts/:id/verify-ghana-card -->
<!-- Backend creates Didit session → returns session_url -->
<!-- Agent gives URL to customer → customer verifies on their phone -->
<!-- Webhook fires → account gets updated -->
```

---

### ⚫ HOUR 44-48: Testing & Deploy

**18. Deploy Dashboard**
```bash
cd dealer-dashboard
npm run build
npx wrangler pages deploy .svelte-kit/cloudflare
```

**19. Test End-to-End**
1. Login as Super Admin → verify you see all data
2. Register a new agent → approve them
3. Login as the agent → verify they see ONLY their data
4. Create a customer → verify admin sees it but other agents don't
5. Check notifications → admin should get "new sale" notification
6. Test FCM lock/unlock on a test device

**20. Build APKs**
```bash
# Push code to trigger CI
git add .
git commit -m "feat: multi-tenant hierarchy, agent registration, notifications"
git push origin main
# Wait for GitHub Actions to build both APKs
```

---

## 📋 QUICK REFERENCE: Data Visibility

```
┌─────────────────────────────────────────────────────────────┐
│ ROLE              │ CAN SEE                                  │
├─────────────────────────────────────────────────────────────┤
│ SUPER_ADMIN       │ EVERYTHING (all agencies, all data)      │
│ AGENCY_OWNER      │ Own agency (all branches, agents, data)  │
│ BRANCH_ADMIN      │ Own branch (agents + customers)          │
│ AGENT             │ ONLY own customers + own sales           │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔑 KEY ENVIRONMENT VARIABLES TO ADD

```bash
# Cloudflare Worker Secrets
npx wrangler secret put DIDIT_API_KEY
npx wrangler secret put DIDIT_WEBHOOK_SECRET
npx wrangler secret put DIDIT_WORKFLOW_ID

# GitHub Secrets (for CI)
# HMAC_SECRET — already exists
# SIGNING_CERT_HASH — already exists
# FCM_API_KEY — already exists
# FCM_APPLICATION_ID — already exists
```

---

## 🎯 DEMO CHECKLIST (What Client Must See)

- [ ] Super Admin login → sees everything
- [ ] Agent self-registration → admin approves
- [ ] Agent logs in → sees ONLY their customers
- [ ] Agent enrolls customer → admin gets notification
- [ ] Admin force-locks device → phone locks
- [ ] Payment recorded → phone unlocks
- [ ] Stolen device flagged → GPS tracking shown
- [ ] (Optional) Ghana Card verification via Didit

---

## 🆘 IF SOMETHING BREAKS

**Dashboard won't deploy:**
```bash
# Check for build errors:
npm run check  # TypeScript errors
npm run build  # Build errors
# Fix errors, try again
```

**Migration fails:**
```bash
# Run each ALTER TABLE separately
npx wrangler d1 execute securepay-db --command "ALTER TABLE dealers ADD COLUMN role TEXT NOT NULL DEFAULT 'SUPER_ADMIN';"
# If it says "duplicate column", skip it and move to next
```

**APK build fails:**
```bash
# Check GitHub Actions logs
# Most common: missing env vars → add to GitHub Secrets
# FCM_APPLICATION_ID is critical — without it, FCM doesn't work
```

---

## 💡 TIPS

1. **Don't rewrite what works.** The existing single-dealer code is fine. Just ADD the multi-tenant layer on top.
2. **Test with ADB provisioning** for the demo — it works, the client is OK with it.
3. **Focus on the demo, not perfection.** Get the multi-tenant hierarchy working, then polish later.
4. **The client cares about:** (a) Agent isolation, (b) Admin sees everything, (c) Device locking works. Nail these three.
5. **Didit integration is a bonus** — if you run out of time, just show the Ghana Card photo upload (existing feature) and say "Didit integration is next sprint."
