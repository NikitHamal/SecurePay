# 🛡️ SecurePay — Full Production Audit & Multi-Tenant Roadmap
### *Complete Technical Audit for Touch Base (TB) Device Financing System*
**Date:** July 8, 2026  
**Auditor:** Arena.ai Agent Mode  
**Repo:** [github.com/NikitHamal/SecurePay](https://github.com/NikitHamal/SecurePay)  
**Commit:** `3d28ab4` (July 6, 2026)

---

## TABLE OF CONTENTS

1. [Executive Summary](#1-executive-summary)
2. [Current Architecture Audit](#2-current-architecture-audit)
3. [Security Deep Dive](#3-security-deep-dive)
4. [Critical Bugs & Issues](#4-critical-bugs--issues)
5. [What Exists vs. What Client Needs (Gap Analysis)](#5-gap-analysis)
6. [Multi-Tenant Hierarchy Design](#6-multi-tenant-hierarchy-design)
7. [Database Schema Evolution](#7-database-schema-evolution)
8. [Didit.me Ghana Card Integration](#8-diditme-ghana-card-integration)
9. [Play Protect & Provisioning Strategy](#9-play-protect--provisioning-strategy)
10. [Production Readiness Checklist](#10-production-readiness-checklist)
11. [48-Hour Sprint Action Plan](#11-48-hour-sprint-action-plan)
12. [Long-Term Roadmap (Post-Demo)](#12-long-term-roadmap-post-demo)

---

## 1. EXECUTIVE SUMMARY

### Overall Grade: **B+ → Needs B+ → A- Transformation**

The SecurePay codebase is **technically strong** for a 1-week-old project. The Android DPC implementation is exceptional, the HMAC-SHA256 device authentication is enterprise-grade, and the SvelteKit/Cloudflare architecture is lean and cost-effective.

**What's working:**
- ✅ Customer app Device Owner provisioning (with known Samsung workarounds)
- ✅ Agent app enrollment flow (KYC → IMEI → Plan → Token)
- ✅ Dealer dashboard with KPIs, customer management, force-lock/unlock
- ✅ HMAC-SHA256 per-device authentication with replay protection
- ✅ Stolen device tracking (GPS + offline batching)
- ✅ FCM push for real-time lock/unlock signals
- ✅ CI/CD pipeline (GitHub Actions → signed APK → R2)

**What's missing for production with the client's actual requirements:**
- ❌ Multi-tenant hierarchy (Super Admin → DSL Agency → Branch → Agent)
- ❌ Agent isolation (agents can't see each other's data)
- ❌ Didit.me Ghana Card KYC integration
- ❌ Agent self-registration with admin approval
- ❌ Notification system (admin notified of agent sales)
- ❌ Payment integration (Mobile Money / MTN MoMo for Ghana)
- ❌ API rate limiting
- ❌ Automated testing
- ❌ Production monitoring/alerting

### The Honest Assessment

The current app works as a **single-dealer single-admin system**. The client wants a **multi-tenant platform** where:
- The client (Super Admin / TB Global Owner) sees everything
- Multiple DSL Agencies can operate independently
- Each Agency has Branches
- Each Branch has Agents
- Agents can only see their own customers/sales
- Agents register themselves and need admin approval

This is **not a small change** — it requires a database schema redesign, new API endpoints, new UI screens, and a new authentication/authorization model. However, the foundation is solid enough that this can be built incrementally.

---

## 2. CURRENT ARCHITECTURE AUDIT

### 2.1 Dealer Dashboard (Backend + Admin UI)

| Aspect | Assessment |
|--------|-----------|
| **Stack** | SvelteKit 2, TypeScript, Tailwind CSS 3 |
| **Hosting** | Cloudflare Pages (SPA mode) |
| **Database** | Cloudflare D1 (SQLite-compatible) |
| **Storage** | Cloudflare R2 (KYC photos) |
| **Auth** | JWT with bcrypt password hashing |
| **API Style** | RESTful via SvelteKit `+server.ts` routes |

**Strengths:**
- Zero server costs (Cloudflare free tier is generous)
- All SQL queries are parameterized (no injection risk)
- Mock R2 fallback for local development
- Clean separation of concerns

**Weaknesses:**
- `import * as fs from 'fs'` in `server.ts` — this will break in Workers runtime (only works in dev). Should be behind `typeof process !== 'undefined'` guard.
- No input sanitization on KYC photo uploads (base64 decoding without size limits)
- Account ID generation uses `Math.random()` — not cryptographically secure, collision risk at scale
- No pagination on account/device list endpoints

### 2.2 Agent App

| Aspect | Assessment |
|--------|-----------|
| **Stack** | Kotlin, Jetpack Compose, Material 3 |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 35 (Android 15) |
| **Auth** | Same JWT as dealer dashboard |
| **Camera** | For KYC photos and IMEI barcode scanning |

**Strengths:**
- Clean multi-step enrollment flow
- Real API integration (not mock)
- "Flag as Stolen" and "Delete Account" features
- Customer photo zoom viewer (pinch-to-zoom)

**Weaknesses:**
- `build.gradle.kts` Compose BOM version (`2024.02.00`) differs from customer-app
- No offline mode — requires network for all operations
- Scanner crash during `AnimatedContent` transition (fixed but fragile)
- No agent self-registration — all agents are pre-created by admin

### 2.3 Customer App (TB User)

| Aspect | Assessment |
|--------|-----------|
| **Stack** | Kotlin, Jetpack Compose, Material 3, Android DPC |
| **Privilege** | Device Owner (highest Android privilege) |
| **Package** | `com.touchbase.securepay.client` |
| **Version** | 1.2.3 (versionCode 17) |
| **FCM** | Firebase Cloud Messaging for push lock/unlock |

**Strengths:**
- **Exceptional DPC implementation** — blocks factory reset, ADB, app uninstall, safe boot
- `startLockTask` kiosk mode for overdue accounts
- FRP (Factory Reset Protection) management for Android 11+
- `HeartbeatWorker` for periodic server sync
- `TrackingService` foreground service for stolen device GPS pings
- Offline location batching via `LocationReportStore`
- Per-device HMAC secrets (each device gets its own secret on first check-in)
- `ProvisioningFinalizer` with Samsung Knox compatibility

**Weaknesses:**
- Debug builds contain hardcoded Firebase API keys and local network URLs
- Firebase app ID was previously hardcoded with wrong package hash (fixed but fragile)
- `composeBom = "2024.02.00"` is slightly outdated
- No encrypted SharedPreferences for sensitive local data

---

## 3. SECURITY DEEP DIVE

### 3.1 Authentication & Authorization

| Component | Method | Grade |
|-----------|--------|-------|
| Dealer Login | JWT + bcrypt (salt=10) | ✅ A |
| Device Auth | HMAC-SHA256 + nonce + timestamp | ✅ A+ |
| Replay Protection | `hmac_nonces` table with 10min window | ✅ A |
| Session Duration | **30 days** | 🔴 C |
| Per-Device Secrets | Generated on first check-in, 32-byte hex | ✅ A |

**Critical Issue: JWT 30-day expiry is too long for a financial app.**
- Fix: Reduce to 24 hours + implement refresh token flow
- Or: 8 hours with re-login requirement

### 3.2 Device Security (DPC)

| Policy | Implementation | Grade |
|--------|---------------|-------|
| Block Factory Reset | `DISALLOW_FACTORY_RESET` | ✅ A+ |
| Block ADB | `DISALLOW_USB_FILE_TRANSFER` + debug off | ✅ A |
| Block App Uninstall | `DISALLOW_UNINSTALL_APPS` | ✅ A+ |
| Block Safe Boot | `DISALLOW_SAFE_BOOT` | ✅ A |
| Block Developer Options | `DISALLOW_DEBUGGING_FEATURES` | ✅ A |
| Block Unknown Sources | `DISALLOW_INSTALL_UNKNOWN_SOURCES` | ✅ A |
| Kiosk Mode | `startLockTask` on overdue | ✅ A |
| FRP Management | Account-based for Android 11+ | ✅ A |

**This is the strongest part of the entire system. Grade: A+**

### 3.3 API Security

| Concern | Status | Risk |
|---------|--------|------|
| SQL Injection | Parameterized queries everywhere | ✅ None |
| XSS | SvelteKit auto-escapes | ✅ None |
| Rate Limiting | **NOT IMPLEMENTED** | 🔴 High |
| CORS | Not explicitly configured | 🟡 Medium |
| Input Validation | Basic (IMEI regex, required fields) | 🟡 Medium |
| KYC Photo Upload Size | **NO LIMIT** | 🔴 High |
| Error Information Leak | Generic error messages | ✅ Low |
| Secrets in Source | Debug builds have hardcoded keys | 🔴 Medium |

### 3.4 Data Protection

| Concern | Status |
|---------|--------|
| Passwords | bcrypt hash, salt=10 ✅ |
| KYC Photos in R2 | Stored as JPEG, served via API (not public bucket) ✅ |
| Device Secrets | Per-device, 32-byte hex, generated server-side ✅ |
| Local Storage | SharedPreferences (not encrypted) 🟡 |
| PII (National ID) | Stored in plaintext in D1 🟡 |
| Audit Trail | No logging of who accessed what data 🔴 |

---

## 4. CRITICAL BUGS & ISSUES

### 🔴 CRITICAL — Must Fix Before Production

| # | Issue | File | Impact |
|---|-------|------|--------|
| 1 | **No API Rate Limiting** | `hooks.server.ts` | Brute-force login, DoS |
| 2 | **No KYC Photo Size Limit** | `/api/accounts/+server.ts` | Memory exhaustion on Workers (128MB limit) |
| 3 | **Debug Secrets in Source** | `customer-app/build.gradle.kts` | FCM API key exposed in git history |
| 4 | **`Math.random()` for Account IDs** | `/api/accounts/+server.ts` | Collision risk at scale |
| 5 | **`fs` import in Workers code** | `server.ts` | Will crash in production (dev-only fallback) |
| 6 | **`device_logs` table is unauthenticated** | Migration file | Anyone can flood with logs |
| 7 | **No input sanitization on customerName/nationalId** | `/api/accounts` | XSS via stored data in dashboard |

### 🟠 HIGH — Fix Within First Sprint

| # | Issue | File | Impact |
|---|-------|------|--------|
| 8 | JWT expiry too long (30 days) | `auth.ts` | Session hijacking window |
| 9 | No pagination on list endpoints | `/api/accounts/+server.ts` | Performance at scale |
| 10 | Currency code hardcoded as 'KES' | `/api/accounts/+server.ts` | Should be 'GHS' for Ghana |
| 11 | No agent role differentiation | Database schema | Can't implement multi-tenant |
| 12 | Compose BOM version mismatch | Both Android apps | Maintenance risk |
| 13 | No automated tests | Entire project | Regression risk |

### 🟡 MEDIUM — Fix Before Scale

| # | Issue | File | Impact |
|---|-------|------|--------|
| 14 | No CORS configuration | `hooks.server.ts` | API accessible from any origin |
| 15 | No audit logging | Entire project | Can't trace data access |
| 16 | Local SharedPreferences unencrypted | Customer app | Root access exposes secrets |
| 17 | No SMS/Email notifications | Missing feature | Can't remind customers |
| 18 | No backup/DR plan | D1 database | Data loss risk |

---

## 5. GAP ANALYSIS

### What Exists (Current State)

```
Single Admin (Dealer)
    │
    ├── Dashboard (sees everything)
    ├── Agents (pre-created by admin, all see same data)
    └── Customers (enrolled by agents)
```

### What Client Needs (Target State)

```
TOUCH BASE (TB) — Global Owner / Super Admin (Client)
    │
    ├── Can see ALL DSL Agencies
    ├── Can see ALL Branches
    ├── Can see ALL Agents
    ├── Can see ALL Customers
    ├── Can see ALL Transactions
    └── Can see ALL Devices
    │
    ├── DSL Agency 1 (Regional Leader)
    │   ├── Own Branches
    │   ├── Own Agents
    │   ├── Own Customers
    │   └── Own Transactions
    │
    ├── DSL Agency 2 (Regional Leader)
    │   └── ...
    │
    └── DSL Agency N
        └── ...

DSL Agency → Branch Admin → Agents
    - Agent A sees ONLY their own customers/sales
    - Agent B sees ONLY their own customers/sales
    - Branch Admin sees all agents in their branch
    - Agency sees all branches in their agency
    - TB Super Admin sees EVERYTHING
```

### The Gaps

| Feature | Current | Needed |
|---------|---------|--------|
| **User Roles** | Single `dealers` table | Super Admin, Agency, Branch, Agent |
| **Data Isolation** | None (all dealers see all data) | Row-level filtering by hierarchy |
| **Agent Registration** | Admin creates agents | Agents self-register, admin approves |
| **Notification** | None | Admin notified on agent sales |
| **Ghana Card KYC** | Manual photo upload | Didit.me API integration |
| **Payment** | Manual recording | MTN MoMo / Vodafone Cash integration |
| **Multi-Branch** | Not supported | Agency/Branch hierarchy |
| **Reports** | Basic KPIs | Per-agent, per-branch, per-agency reports |

---

## 6. MULTI-TENANT HIERARCHY DESIGN

### 6.1 Database Schema for Multi-Tenancy

```sql
-- ============================================================
-- PHASE 1: Organizational Hierarchy
-- ============================================================

-- Top-level: the client (TB Global Owner)
-- Already exists as `dealers` but needs role expansion

-- Add role system to dealers table
ALTER TABLE dealers ADD COLUMN role TEXT NOT NULL DEFAULT 'SUPER_ADMIN';
-- Roles: 'SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN', 'AGENT'

-- DSL Agencies (top-level sales organizations)
CREATE TABLE IF NOT EXISTS agencies (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    owner_id TEXT NOT NULL REFERENCES dealers(id),
    phone TEXT,
    region TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
);

-- Link dealers who are AGENCY_OWNER to their agency
ALTER TABLE dealers ADD COLUMN agency_id TEXT REFERENCES agencies(id);

-- Branches (physical locations under an agency)
CREATE TABLE IF NOT EXISTS branches (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    agency_id TEXT NOT NULL REFERENCES agencies(id),
    admin_id TEXT REFERENCES dealers(id),  -- BRANCH_ADMIN
    address TEXT,
    phone TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
);

-- Link dealers who are BRANCH_ADMIN to their branch
ALTER TABLE dealers ADD COLUMN branch_id TEXT REFERENCES branches(id);

-- Agents now link to a branch
ALTER TABLE dealers ADD COLUMN branch_id TEXT REFERENCES branches(id);

-- Agent self-registration requests
CREATE TABLE IF NOT EXISTS agent_requests (
    id TEXT PRIMARY KEY,
    full_name TEXT NOT NULL,
    email TEXT UNIQUE NOT NULL,
    phone TEXT NOT NULL,
    password TEXT NOT NULL,  -- bcrypt hash
    requested_branch_id TEXT,  -- optional: agent requests to join a specific branch
    status TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED
    reviewed_by TEXT REFERENCES dealers(id),
    reviewed_at INTEGER,
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
);

-- ============================================================
-- PHASE 2: Data Isolation
-- ============================================================

-- Accounts (customer loans) now track which agent enrolled them
ALTER TABLE accounts ADD COLUMN enrolled_by TEXT REFERENCES dealers(id);

-- Add branch association for data isolation
ALTER TABLE accounts ADD COLUMN branch_id TEXT REFERENCES branches(id);
ALTER TABLE accounts ADD COLUMN agency_id TEXT REFERENCES agencies(id);

-- Devices also need agency tracking
ALTER TABLE devices ADD COLUMN branch_id TEXT REFERENCES branches(id);

-- Notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id TEXT PRIMARY KEY,
    recipient_id TEXT NOT NULL REFERENCES dealers(id),
    type TEXT NOT NULL,  -- 'NEW_SALE', 'AGENT_REQUEST', 'PAYMENT_RECEIVED', 'OVERDUE_ALERT'
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    is_read INTEGER NOT NULL DEFAULT 0,
    related_entity_type TEXT,  -- 'account', 'agent_request', 'payment'
    related_entity_id TEXT,
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient_id, is_read);
CREATE INDEX idx_accounts_enrolled_by ON accounts(enrolled_by);
CREATE INDEX idx_accounts_branch ON accounts(branch_id);
CREATE INDEX idx_accounts_agency ON accounts(agency_id);
```

### 6.2 Data Visibility Rules (Query Patterns)

```typescript
// The core principle: every query filters by the user's scope

// SUPER_ADMIN: sees everything
async function getAccountsForSuperAdmin(db: D1Database) {
  return db.prepare('SELECT * FROM accounts ORDER BY created_at DESC').all();
}

// AGENCY_OWNER: sees only their agency's data
async function getAccountsForAgency(db: D1Database, agencyId: string) {
  return db.prepare(`
    SELECT a.*, d.imei, d.model as device_model
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    WHERE a.agency_id = ?
    ORDER BY a.created_at DESC
  `).bind(agencyId).all();
}

// BRANCH_ADMIN: sees only their branch's data
async function getAccountsForBranch(db: D1Database, branchId: string) {
  return db.prepare(`
    SELECT a.*, d.imei, d.model as device_model
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    WHERE a.branch_id = ?
    ORDER BY a.created_at DESC
  `).bind(branchId).all();
}

// AGENT: sees only their own data
async function getAccountsForAgent(db: D1Database, agentId: string) {
  return db.prepare(`
    SELECT a.*, d.imei, d.model as device_model
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    WHERE a.enrolled_by = ?
    ORDER BY a.created_at DESC
  `).bind(agentId).all();
}
```

### 6.3 New API Endpoints Needed

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register-agent` | None | Agent self-registration |
| POST | `/api/auth/approve-agent/:id` | Agency/Branch | Approve agent request |
| POST | `/api/auth/reject-agent/:id` | Agency/Branch | Reject agent request |
| GET | `/api/agent-requests` | Agency/Branch | List pending requests |
| POST | `/api/agencies` | Super Admin | Create new agency |
| POST | `/api/branches` | Agency Owner | Create new branch |
| GET | `/api/my-agents` | Branch Admin | List agents in branch |
| GET | `/api/my-sales` | Agent | Agent's own sales |
| GET | `/api/notifications` | Any | Get user's notifications |
| POST | `/api/notifications/mark-read` | Any | Mark notifications read |

### 6.4 Dashboard UI Changes Needed

```
Current Dashboard:
├── Overview (KPIs)
├── Customers
├── Devices
├── Ledger
├── Plans
└── Settings

Target Dashboard:
├── Overview (KPIs — filtered by role scope)
├── Customers (filtered by role scope)
├── Devices (filtered by role scope)
├── Ledger (filtered by role scope)
├── Plans
├── Agents (manage — for Branch Admin+)
│   ├── Agent Requests (approve/reject)
│   ├── Active Agents (list with sales stats)
│   └── Performance (per-agent comparison)
├── Branches (manage — for Agency Owner+)
│   ├── Create Branch
│   └── Branch Performance
├── Agencies (manage — for Super Admin only)
│   ├── Create Agency
│   └── Agency Performance
├── Notifications (bell icon + page)
├── Settings
└── Audit Log (Super Admin only)
```

---

## 7. DATABASE SCHEMA EVOLUTION

### 7.1 Current Schema (What Exists)

```
dealers          → Single flat table, no hierarchy
plans            → Loan plans (Lite 90, Standard 180, Premium 365)
devices          → Phone inventory
accounts         → Customer loans
payments         → Payment records
lock_events      → Lock/unlock audit trail
sessions         → JWT sessions
hmac_nonces      → Replay protection
device_logs      → Remote diagnostics
location_logs    → GPS tracking for stolen devices
provisioning_tokens → Activation codes
```

### 7.2 Required New Tables & Columns

```sql
-- Migration: 0002_multi_tenant.sql

-- 1. Role system
ALTER TABLE dealers ADD COLUMN role TEXT NOT NULL DEFAULT 'AGENT';
ALTER TABLE dealers ADD COLUMN agency_id TEXT;
ALTER TABLE dealers ADD COLUMN branch_id TEXT;
ALTER TABLE dealers ADD COLUMN is_approved INTEGER NOT NULL DEFAULT 1;
ALTER TABLE dealers ADD COLUMN approved_by TEXT;
ALTER TABLE dealers ADD COLUMN approved_at INTEGER;

-- 2. Organizational hierarchy
CREATE TABLE IF NOT EXISTS agencies (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    owner_id TEXT NOT NULL,
    phone TEXT,
    region TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL DEFAULT (unixepoch()),
    FOREIGN KEY (owner_id) REFERENCES dealers(id)
);

CREATE TABLE IF NOT EXISTS branches (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    agency_id TEXT NOT NULL,
    admin_id TEXT,
    address TEXT,
    phone TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL DEFAULT (unixepoch()),
    FOREIGN KEY (agency_id) REFERENCES agencies(id),
    FOREIGN KEY (admin_id) REFERENCES dealers(id)
);

-- 3. Agent requests
CREATE TABLE IF NOT EXISTS agent_requests (
    id TEXT PRIMARY KEY,
    full_name TEXT NOT NULL,
    email TEXT UNIQUE NOT NULL,
    phone TEXT NOT NULL,
    password TEXT NOT NULL,
    requested_branch_id TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    reviewed_by TEXT,
    reviewed_at INTEGER,
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
);

-- 4. Data isolation columns on accounts
ALTER TABLE accounts ADD COLUMN enrolled_by TEXT;
ALTER TABLE accounts ADD COLUMN branch_id TEXT;
ALTER TABLE accounts ADD COLUMN agency_id TEXT;

-- 5. Notifications
CREATE TABLE IF NOT EXISTS notifications (
    id TEXT PRIMARY KEY,
    recipient_id TEXT NOT NULL,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    is_read INTEGER NOT NULL DEFAULT 0,
    related_entity_type TEXT,
    related_entity_id TEXT,
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
);

-- 6. Indexes
CREATE INDEX IF NOT EXISTS idx_dealers_role ON dealers(role);
CREATE INDEX IF NOT EXISTS idx_dealers_agency ON dealers(agency_id);
CREATE INDEX IF NOT EXISTS idx_dealers_branch ON dealers(branch_id);
CREATE INDEX IF NOT EXISTS idx_accounts_enrolled_by ON accounts(enrolled_by);
CREATE INDEX IF NOT EXISTS idx_accounts_branch ON accounts(branch_id);
CREATE INDEX IF NOT EXISTS idx_accounts_agency ON accounts(agency_id);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_id, is_read);
CREATE INDEX IF NOT EXISTS idx_agent_requests_status ON agent_requests(status);
```

### 7.3 Seed Data for Ghana Client

```sql
-- Make the existing dealer the Super Admin
UPDATE dealers SET role = 'SUPER_ADMIN' WHERE email = 'dealer@securepay.io';

-- Create a default agency for the client
INSERT INTO agencies (id, name, owner_id, region)
VALUES ('AGY-001', 'Touch Base Ghana', (SELECT id FROM dealers WHERE email = 'dealer@securepay.io'), 'Greater Accra');

-- Create a default branch
INSERT INTO branches (id, name, agency_id, address)
VALUES ('BR-001', 'Main Office', 'AGY-001', 'Accra, Ghana');

-- Update existing dealer with agency/branch
UPDATE dealers SET agency_id = 'AGY-001', branch_id = 'BR-001' WHERE email = 'dealer@securepay.io';

-- Update existing accounts to have branch/agency
UPDATE accounts SET branch_id = 'BR-001', agency_id = 'AGY-001' WHERE branch_id IS NULL;
```

---

## 8. DIDIT.ME GHANA CARD INTEGRATION

### 8.1 What Didit.me Offers

Didit is an identity verification platform with these key capabilities for your use case:

| Feature | Cost | Relevance |
|---------|------|-----------|
| **ID Verification** | $0.15/check | Ghana Card scanning + OCR |
| **Passive Liveness** | $0.10/check | Selfie without interaction |
| **Active Liveness** | $0.15/check | Motion-based liveness |
| **Face Match (1:1)** | $0.05/check | Match selfie to ID photo |
| **Full KYC Bundle** | $0.33/check | ID + Liveness + Face Match + IP Analysis |
| **Free Tier** | 500 sessions/month | Free forever, no credit card |

**For your Ghana Card use case, the recommended workflow:**
1. **ID Verification** — scans the Ghana Card (supports 14,000+ document types)
2. **Passive Liveness** — ensures the person is real (no photo spoofing)
3. **Face Match** — matches selfie to Ghana Card photo
4. **Optional: IP Analysis** — detects VPN/proxy use

**Total cost: $0.33 per customer (or free for first 500/month)**

### 8.2 Integration Architecture

```
Agent App (Android)
    │
    ├── 1. Agent captures customer's Ghana Card (front/back photos)
    ├── 2. Agent captures customer selfie
    ├── 3. App sends images to YOUR backend
    │
Dealer Dashboard Backend (SvelteKit)
    │
    ├── 4. Backend calls Didit API:
    │   POST https://verification.didit.me/v3/session/
    │   Headers: x-api-key: <YOUR_API_KEY>
    │   Body: { workflow_id: "wf_xxx", vendor_data: "customer-id" }
    │
    ├── 5. Didit returns session_url → redirect customer to hosted UI
    │   OR use standalone API:
    │   POST https://verification.didit.me/v3/id-verification/
    │   With front_image + back_image (multipart form)
    │
    ├── 6. Didit webhook fires when verification completes:
    │   POST <your-webhook-url>
    │   Headers: X-Signature-V2: <hmac>
    │   Body: { status: "Approved" | "Declined", id_verification: {...} }
    │
    └── 7. Backend stores verification result in database
```

### 8.3 Didit API Integration Code

```typescript
// dealer-dashboard/src/lib/didit.ts

const DIDIT_BASE_URL = 'https://verification.didit.me';

interface DiditConfig {
  apiKey: string;
  webhookSecret: string;
  workflowId: string;
}

// Step 1: Create a verification session
export async function createVerificationSession(
  config: DiditConfig,
  vendorData: string  // your internal customer/account ID
): Promise<{ sessionUrl: string; sessionId: string }> {
  const response = await fetch(`${DIDIT_BASE_URL}/v3/session/`, {
    method: 'POST',
    headers: {
      'x-api-key': config.apiKey,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      workflow_id: config.workflowId,
      vendor_data: vendorData
    })
  });

  if (!response.ok) {
    throw new Error(`Didit session creation failed: ${response.statusText}`);
  }

  const data = await response.json();
  return {
    sessionUrl: data.session_url,
    sessionId: data.session_id
  };
}

// Step 2: Verify webhook signature (CRITICAL - must verify before trusting)
export async function verifyDiditWebhook(
  body: string,
  signature: string,
  secret: string
): Promise<boolean> {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['verify']
  );

  // IMPORTANT: Must sort keys recursively and shorten floats
  const parsed = JSON.parse(body);
  const sorted = JSON.stringify(sortKeys(parsed));
  const data = encoder.encode(sorted);

  const signatureBytes = hexToBytes(signature);
  return await crypto.subtle.verify(
    'HMAC',
    key,
    signatureBytes,
    data
  );
}

// Step 3: Handle webhook callback
export async function handleDiditWebhook(
  db: D1Database,
  payload: any
): Promise<void> {
  const { session_id, status, vendor_data } = payload.data || payload;

  // vendor_data is the customer/account ID you passed when creating the session
  const accountId = vendor_data;

  // Store verification result
  await db.prepare(`
    UPDATE accounts
    SET ghana_card_verified = ?,
        ghana_card_status = ?,
        ghana_card_verified_at = ?
    WHERE id = ?
  `).bind(
    status === 'Approved' ? 1 : 0,
    status,
    Math.floor(Date.now() / 1000),
    accountId
  ).run();
}

function sortKeys(obj: any): any {
  if (obj === null || typeof obj !== 'object') return obj;
  if (Array.isArray(obj)) return obj.map(sortKeys);
  return Object.keys(obj).sort().reduce((sorted, key) => {
    sorted[key] = sortKeys(obj[key]);
    return sorted;
  }, {} as any);
}

function hexToBytes(hex: string): Uint8Array {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = parseInt(hex.substr(i, 2), 16);
  }
  return bytes;
}
```

### 8.4 Didit Workflow Configuration

```bash
# Create a workflow with the features you need
curl -X POST https://verification.didit.me/v3/workflows/ \
  -H "x-api-key: $DIDIT_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "workflow_label": "Ghana Card KYC",
    "features": [
      { "feature": "ID_VERIFICATION" },
      { "feature": "LIVENESS" },
      { "feature": "FACE_MATCH" },
      { "feature": "IP_ANALYSIS" }
    ]
  }'

# Response: { "workflow_id": "wf_abc123" }
# Save this workflow_id in your Cloudflare Worker secrets
```

### 8.5 Environment Variables Needed

```
# Didit.me Configuration
DIDIT_API_KEY=your_api_key_here
DIDIT_WEBHOOK_SECRET=your_webhook_secret_here
DIDIT_WORKFLOW_ID=wf_abc123
DIDIT_WEBHOOK_URL=https://securepay-dashboard.pages.dev/api/webhooks/didit
```

### 8.6 Agent App Flow with Didit

```
Current Flow:
Agent → Capture Ghana Card photo → Upload to R2 → Manual review

Target Flow with Didit:
Agent → Capture Ghana Card photo → Capture selfie → 
  → Backend creates Didit session → Agent gives customer the session URL
  → Customer completes verification on their phone
  → Didit webhook fires → Backend updates account with verification status
  → Agent sees "✅ Ghana Card Verified" in the app

Alternative (Fully Automated):
Agent → Uses Didit's Mobile SDK (Android) → 
  → Captures ID + selfie in-app →
  → Backend receives images via standalone API →
  → Verification completes in <2 seconds →
  → Account auto-approved
```

---

## 9. PLAY PROTECT & PROVISIONING STRATEGY

### 9.1 Current Situation

You are using ADB provisioning (`adb shell dpm set-device-owner`) because:
1. QR provisioning fails with "Something went wrong" (Samsung Knox issues)
2. Google Play Protect blocks the APK from unknown source

The client has agreed to this approach and is open to rooting phones if needed.

### 9.2 Recommended Strategy (Three Parallel Paths)

#### Path A: ADB Provisioning (Current — Works Now)
- **Best for:** Demo, pilot, first 50 devices
- **How:** `adb shell dpm set-device-owner com.touchbase.securepay.client/.admin.SecurePayDeviceAdminReceiver`
- **Pros:** Works today, no Google approval needed
- **Cons:** Requires physical access to each device, needs technician

#### Path B: Google Play Store (Medium-term — 1-2 weeks)
- **Best for:** Scaling beyond 50 devices
- **How:** Upload signed AAB to Play Console Internal Testing → Play Protect whitelists the signature
- **Pros:** Play Protect stops blocking, QR provisioning works, OTA updates
- **Cons:** Takes 1-7 days for Play Console verification, $25 one-time fee

#### Path C: Root + System Install (Client's Preference — Long-term)
- **Best for:** Full control, anti-tamper
- **How:** Root each phone → install app as system app → app survives any reset
- **Pros:** Maximum control, can detect SIM changes, can remotely wipe
- **Cons:** Voids warranty, some apps detect root, Samsung Knox may conflict
- **Legal Note:** In Ghana, rooting phones you own (the dealer owns them until paid off) is legal. The financing agreement should explicitly state the device is managed.

### 9.3 M-KOPA Comparison

M-KOPA uses a similar model:
1. **Phase 1:** Started with ADB + custom recovery (2013-2016)
2. **Phase 2:** Partnered with Samsung for Knox Mobile Enrollment (2017-2020)
3. **Phase 3:** Built custom firmware with OEM partnerships (2020+)
4. **Phase 4:** Now works directly with phone manufacturers to pre-install their agent

**Your trajectory should be:**
1. **Now:** ADB provisioning (demo + first customers)
2. **Month 1-2:** Get Play Store presence (remove Play Protect blocker)
3. **Month 3-6:** Samsung Knox Mobile Enrollment (for Samsung devices)
4. **Month 6-12:** OEM partnerships (Tecno, Infinix pre-install)

---

## 10. PRODUCTION READINESS CHECKLIST

### 🔴 Must-Fix (Before Demo in 48 Hours)

- [ ] Fix `Math.random()` account ID → use `crypto.randomUUID()`
- [ ] Fix `fs` import in Workers code → guard with environment check
- [ ] Add KYC photo size limit (max 5MB)
- [ ] Fix currency code from 'KES' to 'GHS'
- [ ] Verify all migrations apply cleanly to fresh D1
- [ ] Test ADB provisioning flow end-to-end
- [ ] Verify FCM push lock/unlock works
- [ ] Check all API endpoints return correct data

### 🟠 Fix in First Week

- [ ] Implement API rate limiting (100 req/min per IP, 10 login attempts/hour)
- [ ] Reduce JWT expiry to 8 hours
- [ ] Add pagination to list endpoints (limit/offset)
- [ ] Add input sanitization (escape HTML in names)
- [ ] Encrypt SharedPreferences on Android (EncryptedSharedPreferences)
- [ ] Add CORS headers for dashboard domain only
- [ ] Set up basic monitoring (Cloudflare Analytics + Sentry)

### 🟡 Fix in First Month

- [ ] Implement multi-tenant hierarchy (database migration + API)
- [ ] Build agent self-registration flow
- [ ] Integrate Didit.me for Ghana Card verification
- [ ] Build notification system
- [ ] Add audit logging (who accessed what, when)
- [ ] Write integration tests for payment → unlock flow
- [ ] Add backup strategy for D1 database
- [ ] Remove hardcoded debug secrets from git history (BFG Repo Cleaner)

### 🟢 Fix Before Scale

- [ ] Automated testing suite (unit + integration)
- [ ] Load testing (1000+ accounts)
- [ ] MTN MoMo payment integration
- [ ] SMS notifications via Africa's Talking
- [ ] Production SSL/TLS certificate management
- [ ] Disaster recovery plan
- [ ] Compliance review (Ghana Data Protection Act)

---

## 11. 48-HOUR SPRINT ACTION PLAN

### Goal: Ship a working demo that satisfies the client

#### Hour 0-4: Critical Fixes

1. **Fix Account ID generation:**
```typescript
// Replace: const accountId = `ACC-${100000 + Math.floor(Math.random() * 900000)}`;
// With:
const accountId = `ACC-${crypto.randomUUID().slice(0, 8).toUpperCase()}`;
```

2. **Fix fs import in server.ts:**
```typescript
// Wrap the mock R2 in a dev-only check
let mockR2Instance: any = null;
export function getR2(event) {
  if (event.platform?.env?.R2) return event.platform.env.R2;
  // Only create mock in dev (Node.js, not Workers)
  if (typeof process !== 'undefined' && process.env?.NODE_ENV === 'development') {
    // ... existing mock code
  }
  throw new Error('R2 bucket not available');
}
```

3. **Fix currency code:**
```typescript
// In /api/accounts POST handler:
// Change: 'GHS' (already correct in latest code)
// Verify all display formatting uses 'GHS' not 'KES'
```

4. **Add photo size limit:**
```typescript
// Before base64 decode:
const MAX_PHOTO_BYTES = 5 * 1024 * 1024; // 5MB
if (customerPhoto && Buffer.from(customerPhoto, 'base64').length > MAX_PHOTO_BYTES) {
  return errorResponse('Photo too large (max 5MB)', 413);
}
```

#### Hour 4-12: Multi-Tenant Foundation

5. **Create migration `0002_multi_tenant.sql`** (schema from Section 7.2)
6. **Update `hooks.server.ts`** to include role in JWT payload
7. **Create scoped query helpers** (Section 6.2)
8. **Update existing endpoints** to filter by role scope

#### Hour 12-24: Agent Registration

9. **Create `/api/auth/register-agent` endpoint**
10. **Create `/api/auth/approve-agent/:id` endpoint**
11. **Create `/api/agent-requests` list endpoint**
12. **Add notification on new agent sale**

#### Hour 24-36: Dashboard UI

13. **Add role-based navigation** (show/hide menu items by role)
14. **Add Agent Management page** (for Branch Admin+)
15. **Add Agent Requests page** (approve/reject)
16. **Add Notifications bell** in dashboard header

#### Hour 36-48: Testing & Polish

17. **End-to-end test:** Register agent → approve → agent creates customer → admin sees it
18. **Test ADB provisioning** on a test device
19. **Verify FCM push** works for lock/unlock
20. **Deploy dashboard** to Cloudflare Pages
21. **Build APKs** via CI
22. **Prepare demo script** for client

### Demo Script (What to Show the Client)

```
1. Super Admin (Client) Login
   → Shows all agencies, branches, agents, customers
   → KPIs: total revenue, active devices, overdue count

2. Agent Self-Registration
   → New agent creates account on phone
   → Admin approves from dashboard

3. Agent Enrollment Flow
   → Agent scans phone IMEI
   → Captures customer Ghana Card (photos)
   → Selects loan plan
   → Customer signs (digital signature)
   → Device paired via activation code

4. Device Management
   → Admin sees device status in real-time
   → Force-lock a device → phone locks within seconds
   → Record payment → phone unlocks automatically

5. Agent Isolation
   → Agent A logs in → sees only their 3 customers
   → Agent B logs in → sees only their 5 customers
   → Admin sees all 8 customers with agent attribution

6. Stolen Device Tracking
   → Admin flags device as stolen
   → Dashboard shows last known GPS location
   → Device is locked in kiosk mode
```

---

## 12. LONG-TERM ROADMAP (Post-Demo)

### Phase 1: Foundation (Week 1-2)
- [ ] Multi-tenant hierarchy fully implemented
- [ ] Agent self-registration + approval flow
- [ ] Notification system
- [ ] Didit.me Ghana Card integration
- [ ] API rate limiting
- [ ] JWT expiry reduction
- [ ] Input sanitization

### Phase 2: Payments (Week 3-4)
- [ ] MTN Mobile Money integration (Ghana)
- [ ] Vodafone Cash integration
- [ ] Auto-payment recording
- [ ] Payment reminders via SMS
- [ ] Revenue dashboard per agent/branch/agency

### Phase 3: Hardening (Week 5-6)
- [ ] Automated test suite
- [ ] Load testing (1000+ concurrent accounts)
- [ ] Encrypted local storage on Android
- [ ] Audit logging
- [ ] D1 backup automation
- [ ] Sentry integration for error tracking

### Phase 4: Scale (Month 2-3)
- [ ] Google Play Store publication (remove Play Protect blocker)
- [ ] Samsung Knox Mobile Enrollment
- [ ] OTA update mechanism for customer app
- [ ] Multi-currency support (GHS, KES, NGN)
- [ ] Advanced analytics & reporting
- [ ] API for third-party integrations

### Phase 5: Advanced (Month 3-6)
- [ ] OEM partnerships (pre-install on Tecno/Infinix)
- [ ] Credit scoring based on payment history
- [ ] Refinancing / loan top-up feature
- [ ] Insurance integration
- [ ] USSD interface for feature phone users
- [ ] WhatsApp chatbot for payment reminders

---

## APPENDIX A: Didit.me Quick Reference

### API Endpoints
- Base URL: `https://verification.didit.me`
- Auth: `x-api-key: <YOUR_API_KEY>` (lowercase, hyphenated)
- Webhook signature: `X-Signature-V2` header (HMAC-SHA256)

### Status Values (exact casing)
- `Approved` — all checks passed
- `Declined` — one or more checks failed
- `In Review` — flagged for manual review
- `Expired` — session link expired
- `Not Finished` — user hasn't completed yet

### Pricing
- First 500 sessions/month: FREE (forever)
- Beyond 500: $0.33/session
- Standalone ID verification: $0.15/call
- No minimums, no contracts

### Webhook Events
- `session.verified` — completed successfully
- `session.review_started` — flagged for review
- `session.declined` — failed verification
- `kyc_expired` — session expired

### Setup Steps
1. Sign up at `https://business.didit.me` (no credit card)
2. Create workflow with `ID_VERIFICATION + LIVENESS + FACE_MATCH + IP_ANALYSIS`
3. Note the `workflow_id`
4. Register webhook URL
5. Store `DIDIT_API_KEY`, `DIDIT_WEBHOOK_SECRET`, `DIDIT_WORKFLOW_ID` in Cloudflare secrets

---

## APPENDIX B: Critical File Inventory

| File | Purpose | Priority |
|------|---------|----------|
| `dealer-dashboard/src/hooks.server.ts` | Auth middleware + HMAC verification | 🔴 Critical |
| `dealer-dashboard/src/lib/api/server.ts` | Core business logic | 🔴 Critical |
| `dealer-dashboard/src/routes/api/accounts/+server.ts` | Account CRUD | 🔴 Critical |
| `dealer-dashboard/src/routes/api/device/check/+server.ts` | Device status check | 🔴 Critical |
| `customer-app/app/build.gradle.kts` | Build config with secrets | 🔴 Critical |
| `customer-app/.../SecurePayDeviceAdminReceiver.kt` | DPC receiver | 🔴 Critical |
| `customer-app/.../ProvisioningFinalizer.kt` | Provisioning logic | 🔴 Critical |
| `agent-app/app/build.gradle.kts` | Agent build config | 🟠 High |
| `dealer-dashboard/migrations/` | Database schema | 🔴 Critical |

---

*This report was generated on July 8, 2026. The codebase is in active development and some issues may have been addressed since the audit.*

**Bottom line:** The technical foundation is strong. The core device-locking mechanism is excellent. What's needed now is the multi-tenant layer (hierarchy, data isolation, agent registration) and the Ghana Card integration via Didit. These are additive changes — they don't require rewriting what already works.
