import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, generateToken, errorResponse } from '$lib/api/server';

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const allowedRoles = ['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'];
  if (!allowedRoles.includes(locals.dealer.role)) {
    return errorResponse('Insufficient permissions', 403);
  }

  const { requestId, branchId } = await request.json();
  if (!requestId) return errorResponse('requestId required', 400);

  const db = getDb({ platform });

  const req = await db.prepare('SELECT * FROM agent_requests WHERE id = ? AND status = ?')
    .bind(requestId, 'PENDING').first();

  if (!req) return errorResponse('Request not found or already processed', 404);

  const now = Math.floor(Date.now() / 1000);
  const assignedBranch = branchId || req.requested_branch_id || locals.dealer.branchId;

  const branch = await db.prepare('SELECT agency_id FROM branches WHERE id = ?')
    .bind(assignedBranch).first();
  const agencyId = branch?.agency_id || locals.dealer.agencyId;

  const dealerId = `DLR-${generateToken(6).toUpperCase()}`;
  await db.prepare(`
    INSERT INTO dealers (id, name, email, phone, password, role, agency_id, branch_id, is_approved, approved_by, approved_at, created_at)
    VALUES (?, ?, ?, ?, ?, 'AGENT', ?, ?, 1, ?, ?, ?)
  `).bind(dealerId, req.full_name, req.email, req.phone, req.password,
    agencyId, assignedBranch, locals.dealer.id, now, now).run();

  await db.prepare('UPDATE agent_requests SET status = ?, reviewed_by = ?, reviewed_at = ? WHERE id = ?')
    .bind('APPROVED', locals.dealer.id, now, requestId).run();

  const adminId = (await db.prepare("SELECT id FROM dealers WHERE role = 'SUPER_ADMIN' LIMIT 1").first())?.id;
  if (adminId) {
    await db.prepare(`
      INSERT INTO notifications (id, recipient_id, type, title, message, related_entity_type, related_entity_id, created_at)
      VALUES (?, ?, 'AGENT_APPROVED', ?, ?, 'dealer', ?, ?)
    `).bind(`NOTIF-${generateToken(4).toUpperCase()}`, adminId, 'New Agent Approved',
      `Agent ${req.full_name} has been approved and assigned to branch ${assignedBranch}`,
      dealerId, now).run();
  }

  return json({ message: 'Agent approved successfully', dealerId });
};
