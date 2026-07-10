import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (!['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'].includes(locals.dealer.role)) {
    return errorResponse('Insufficient permissions', 403);
  }

  const body = await request.json() as Record<string, unknown>;
  const requestId = String(body.requestId ?? '').trim();
  if (!requestId) return errorResponse('requestId required', 400);

  const db = getDb({ platform });
  const req = await db.prepare(`
    SELECT ar.id, ar.requested_branch_id, b.agency_id
      FROM agent_requests ar
      LEFT JOIN branches b ON b.id = ar.requested_branch_id
     WHERE ar.id = ? AND ar.status = 'PENDING'
  `).bind(requestId).first();
  if (!req) return errorResponse('Request not found or already processed', 404);

  const branchId = String(req.requested_branch_id ?? '');
  const agencyId = String(req.agency_id ?? '');
  if (!branchId && locals.dealer.role !== 'SUPER_ADMIN') {
    return errorResponse('Unassigned requests must be reviewed by the Super Admin', 403);
  }
  if (locals.dealer.role === 'AGENCY_OWNER' && agencyId !== locals.dealer.agencyId) {
    return errorResponse('Registration request is outside your agency', 403);
  }
  if (locals.dealer.role === 'BRANCH_ADMIN' && branchId !== locals.dealer.branchId) {
    return errorResponse('Registration request is outside your branch', 403);
  }

  const now = Math.floor(Date.now() / 1000);
  await db.prepare(
    "UPDATE agent_requests SET status = 'REJECTED', reviewed_by = ?, reviewed_at = ? WHERE id = ? AND status = 'PENDING'"
  ).bind(locals.dealer.id, now, requestId).run();

  return json({ message: 'Agent request rejected' });
};
