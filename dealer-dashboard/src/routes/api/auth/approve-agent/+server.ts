import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, generateToken, errorResponse } from '$lib/api/server';

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (!['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'].includes(locals.dealer.role)) {
    return errorResponse('Insufficient permissions', 403);
  }

  const body = await request.json() as Record<string, unknown>;
  const requestId = String(body.requestId ?? '').trim();
  const requestedAssignment = String(body.branchId ?? '').trim();
  if (!requestId) return errorResponse('requestId required', 400);

  const db = getDb({ platform });
  const req = await db.prepare(
    `SELECT ar.*, b.agency_id AS requested_agency_id
       FROM agent_requests ar
       LEFT JOIN branches b ON b.id = ar.requested_branch_id
      WHERE ar.id = ? AND ar.status = 'PENDING'`
  ).bind(requestId).first();
  if (!req) return errorResponse('Request not found or already processed', 404);

  const originalBranch = String(req.requested_branch_id ?? '').trim();
  const assignedBranch = requestedAssignment || originalBranch || String(locals.dealer.branchId ?? '').trim();
  if (!assignedBranch) return errorResponse('A valid branch assignment is required', 400);

  const branch = await db.prepare('SELECT id, agency_id FROM branches WHERE id = ? AND is_active = 1')
    .bind(assignedBranch).first();
  if (!branch) return errorResponse('Branch not found', 404);

  const assignedAgency = String(branch.agency_id ?? '');
  if (locals.dealer.role === 'AGENCY_OWNER' && assignedAgency !== locals.dealer.agencyId) {
    return errorResponse('Cannot approve an agent outside your agency', 403);
  }
  if (locals.dealer.role === 'BRANCH_ADMIN' && assignedBranch !== locals.dealer.branchId) {
    return errorResponse('Cannot approve an agent outside your branch', 403);
  }
  if (originalBranch) {
    const originalAgency = String(req.requested_agency_id ?? '');
    if (locals.dealer.role === 'AGENCY_OWNER' && originalAgency !== locals.dealer.agencyId) {
      return errorResponse('Registration request is outside your agency', 403);
    }
    if (locals.dealer.role === 'BRANCH_ADMIN' && originalBranch !== locals.dealer.branchId) {
      return errorResponse('Registration request is outside your branch', 403);
    }
  } else if (locals.dealer.role !== 'SUPER_ADMIN') {
    return errorResponse('Unassigned requests must be reviewed by the Super Admin', 403);
  }

  const existing = await db.prepare('SELECT id FROM dealers WHERE lower(email) = lower(?)')
    .bind(String(req.email)).first();
  if (existing) return errorResponse('Email is already registered', 409);

  const now = Math.floor(Date.now() / 1000);
  const dealerId = `DLR-${generateToken(6).toUpperCase()}`;
  await db.batch([
    db.prepare(`
      INSERT INTO dealers (id, name, email, phone, password, role, agency_id, branch_id, is_approved, approved_by, approved_at, created_at)
      VALUES (?, ?, ?, ?, ?, 'AGENT', ?, ?, 1, ?, ?, ?)
    `).bind(dealerId, req.full_name, req.email, req.phone, req.password,
      assignedAgency, assignedBranch, locals.dealer.id, now, now),
    db.prepare(
      "UPDATE agent_requests SET status = 'APPROVED', reviewed_by = ?, reviewed_at = ? WHERE id = ? AND status = 'PENDING'"
    ).bind(locals.dealer.id, now, requestId)
  ]);

  const recipients = await db.prepare(`
    SELECT id FROM dealers
     WHERE role = 'SUPER_ADMIN'
        OR (role = 'AGENCY_OWNER' AND agency_id = ?)
        OR (role = 'BRANCH_ADMIN' AND branch_id = ?)
  `).bind(assignedAgency, assignedBranch).all();
  for (const recipient of recipients.results) {
    await db.prepare(`
      INSERT INTO notifications (id, recipient_id, type, title, message, related_entity_type, related_entity_id, created_at)
      VALUES (?, ?, 'AGENT_APPROVED', ?, ?, 'dealer', ?, ?)
    `).bind(
      `NOTIF-${generateToken(8).toUpperCase()}`,
      String(recipient.id),
      'New Agent Approved',
      `Agent ${String(req.full_name)} has been approved for branch ${assignedBranch}`,
      dealerId,
      now
    ).run();
  }

  return json({ message: 'Agent approved successfully', dealerId });
};
