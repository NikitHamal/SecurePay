import type { D1Database } from '@cloudflare/workers-types';
import { v4 as uuidv4 } from 'uuid';

/**
 * Agent activity log — the accountability trail the client asked for.
 *
 * Every meaningful staff action (login, enrollment, payment, device
 * registration, customer edits, agent application decisions and deletions)
 * is appended here with the actor, a human label, the customer it touched,
 * the branch it happened at and (when the device supplied it) the GPS fix.
 *
 * Writes are best-effort: audit failure must never break a business action.
 *
 * Action vocabulary (keep stable — the dashboard filter chips rely on it):
 *   LOGIN               staff signed in (dashboard or agent app)
 *   CUSTOMER_CREATED    enrollment submitted
 *   CUSTOMER_EDITED     account fields changed
 *   CUSTOMER_DELETED    account removed (admin only)
 *   PAYMENT_RECORDED    manual payment captured
 *   DEVICE_REGISTERED   phone added to inventory (carries GPS when captured)
 *   AGENT_APPROVED      agent application approved
 *   AGENT_REJECTED      agent application rejected
 */
export type ActivityAction =
  | 'LOGIN'
  | 'CUSTOMER_CREATED'
  | 'CUSTOMER_EDITED'
  | 'CUSTOMER_DELETED'
  | 'PAYMENT_RECORDED'
  | 'DEVICE_REGISTERED'
  | 'DEVICE_REGISTERED_AND_ASSIGNED'
  | 'DEVICE_ASSIGNED'
  | 'DEVICE_UNASSIGNED'
  | 'DOWN_PAYMENT_CONFIRMED'
  | 'DOWN_PAYMENT_REJECTED'
  | 'AGENT_APPROVED'
  | 'AGENT_REJECTED';

export interface ActivityActor {
  id: string;
  name: string;
  role: string;
  agencyId?: string | null;
  branchId?: string | null;
}

export interface ActivityEntryInput {
  actor: ActivityActor;
  action: ActivityAction;
  details: string;
  customerName?: string | null;
  accountId?: string | null;
  imei?: string | null;
  latitude?: number | null;
  longitude?: number | null;
}

let ensureAgentActivityPromise: Promise<void> | null = null;

/** Runtime guard mirroring migrations/20260801_accountability.sql so a fresh or partially migrated D1 never 500s. */
export function ensureAgentActivityTable(db: D1Database): Promise<void> {
  if (!ensureAgentActivityPromise) {
    ensureAgentActivityPromise = db.prepare(`
      CREATE TABLE IF NOT EXISTS agent_activity (
        id            TEXT PRIMARY KEY,
        actor_id      TEXT NOT NULL,
        actor_name    TEXT NOT NULL,
        actor_role    TEXT NOT NULL,
        action        TEXT NOT NULL,
        details       TEXT NOT NULL DEFAULT '',
        customer_name TEXT,
        account_id    TEXT,
        imei          TEXT,
        branch_name   TEXT,
        agency_id     TEXT,
        branch_id     TEXT,
        latitude      REAL,
        longitude     REAL,
        created_at    INTEGER NOT NULL DEFAULT (unixepoch())
      )
    `).run().then(() => undefined).catch((error) => {
      ensureAgentActivityPromise = null;
      throw error;
    });
  }
  return ensureAgentActivityPromise;
}

async function resolveBranchName(db: D1Database, branchId: string | null | undefined): Promise<string | null> {
  if (!branchId) return null;
  try {
    const row = await db.prepare('SELECT name FROM branches WHERE id = ?').bind(branchId).first<{ name: string }>();
    return row?.name ?? null;
  } catch {
    return null;
  }
}

/** Append one entry to the activity log. Never throws. */
export async function logActivity(db: D1Database, input: ActivityEntryInput): Promise<void> {
  try {
    await ensureAgentActivityTable(db);
    const branchName = await resolveBranchName(db, input.actor.branchId);
    await db.prepare(`
      INSERT INTO agent_activity (
        id, actor_id, actor_name, actor_role, action, details,
        customer_name, account_id, imei, branch_name, agency_id, branch_id,
        latitude, longitude, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      uuidv4(),
      input.actor.id,
      String(input.actor.name || '').slice(0, 120),
      String(input.actor.role || 'AGENT').slice(0, 24),
      input.action,
      String(input.details || '').slice(0, 500),
      input.customerName ? String(input.customerName).slice(0, 120) : null,
      input.accountId ?? null,
      input.imei ?? null,
      branchName,
      input.actor.agencyId ?? null,
      input.actor.branchId ?? null,
      typeof input.latitude === 'number' && Number.isFinite(input.latitude) ? input.latitude : null,
      typeof input.longitude === 'number' && Number.isFinite(input.longitude) ? input.longitude : null,
      Math.floor(Date.now() / 1000)
    ).run();
  } catch (error) {
    // Audit must be invisible to production flows; surface in logs only.
    console.error('Failed to write agent activity', error);
  }
}

/** WHERE fragment that scopes the feed to what the caller is allowed to see. */
export function activityScopeFilter(actor: ActivityActor, alias = 'act'): { where: string; params: string[] } {
  switch (actor.role) {
    case 'SUPER_ADMIN':
      return { where: '1=1', params: [] };
    case 'AGENCY_OWNER':
      return { where: `${alias}.agency_id = ?`, params: [actor.agencyId || '__missing_agency__'] };
    case 'BRANCH_ADMIN':
      return { where: `${alias}.branch_id = ?`, params: [actor.branchId || '__missing_branch__'] };
    case 'AGENT':
      return { where: `${alias}.actor_id = ?`, params: [actor.id] };
    default:
      return { where: '1=0', params: [] };
  }
}

/**
 * Normalize a national ID (Ghana Card etc.) for duplicate comparison:
 * uppercase, strip everything that is not a letter/digit so
 * "GHA-123456789-0", "gha1234567890" and "GHA 123456789 0" all collide.
 */
export function normalizeNationalId(raw: unknown): string {
  return String(raw ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '');
}

/** SQL expression matching accounts against a normalized national ID. */
export const NATIONAL_ID_NORM_SQL =
  "REPLACE(REPLACE(REPLACE(REPLACE(UPPER(TRIM(a.national_id)), '-', ''), ' ', ''), '.', ''), '_', '')";
