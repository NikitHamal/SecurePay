import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { activityScopeFilter, ensureAgentActivityTable } from '$lib/audit';

/**
 * GET /api/activity?limit=200&action=PAYMENT_RECORDED&q=kwame
 *
 * The agent activity feed (accountability tracking). Rows are scoped to the
 * caller's position in the hierarchy: super admins see everything, agency
 * owners their agency, branch admins their branch, and agents only their own
 * actions.
 */
export const GET: RequestHandler = async ({ locals, url, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const limitParam = Number(url.searchParams.get('limit') ?? '200');
  const limit = Number.isSafeInteger(limitParam) ? Math.min(Math.max(limitParam, 1), 500) : 200;
  const action = String(url.searchParams.get('action') ?? '').trim().toUpperCase();
  const query = String(url.searchParams.get('q') ?? '').trim().toLowerCase();

  const db = getDb({ platform });
  await ensureAgentActivityTable(db);
  const scope = activityScopeFilter(locals.dealer);

  const clauses: string[] = [scope.where];
  const params: (string | number)[] = [...scope.params];

  const ACTIONS = new Set([
    'LOGIN', 'CUSTOMER_CREATED', 'CUSTOMER_EDITED', 'CUSTOMER_DELETED',
    'PAYMENT_RECORDED', 'DEVICE_REGISTERED', 'AGENT_APPROVED', 'AGENT_REJECTED'
  ]);
  if (action && ACTIONS.has(action)) {
    clauses.push('act.action = ?');
    params.push(action);
  }
  if (query) {
    clauses.push('(LOWER(act.actor_name) LIKE ? OR LOWER(act.details) LIKE ? OR LOWER(COALESCE(act.customer_name, "")) LIKE ? OR LOWER(COALESCE(act.imei, "")) LIKE ?)');
    const like = `%${query}%`;
    params.push(like, like, like, like);
  }

  const result = await db.prepare(`
    SELECT act.id, act.actor_id, act.actor_name, act.actor_role, act.action, act.details,
           act.customer_name, act.account_id, act.imei, act.branch_name,
           act.latitude, act.longitude, act.created_at
      FROM agent_activity act
     WHERE ${clauses.join(' AND ')}
     ORDER BY act.created_at DESC, act.id DESC
     LIMIT ?
  `).bind(...params, limit).all();

  return json(result.results.map((row) => ({
    id: row.id,
    actorId: row.actor_id,
    actorName: row.actor_name,
    actorRole: row.actor_role,
    action: row.action,
    details: row.details,
    customerName: row.customer_name,
    accountId: row.account_id,
    imei: row.imei,
    branchName: row.branch_name,
    latitude: row.latitude,
    longitude: row.longitude,
    createdAt: Number(row.created_at) * 1000
  })), { headers: { 'Cache-Control': 'no-store' } });
};
