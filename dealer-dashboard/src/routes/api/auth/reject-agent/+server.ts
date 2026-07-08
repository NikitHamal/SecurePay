import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const allowedRoles = ['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'];
  if (!allowedRoles.includes(locals.dealer.role)) {
    return errorResponse('Insufficient permissions', 403);
  }

  const { requestId } = await request.json();
  if (!requestId) return errorResponse('requestId required', 400);

  const db = getDb({ platform });

  const req = await db.prepare('SELECT * FROM agent_requests WHERE id = ? AND status = ?')
    .bind(requestId, 'PENDING').first();

  if (!req) return errorResponse('Request not found or already processed', 404);

  const now = Math.floor(Date.now() / 1000);
  await db.prepare('UPDATE agent_requests SET status = ?, reviewed_by = ?, reviewed_at = ? WHERE id = ?')
    .bind('REJECTED', locals.dealer.id, now, requestId).run();

  return json({ message: 'Agent request rejected' });
};
