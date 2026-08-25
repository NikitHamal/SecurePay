import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { logActivity } from '$lib/audit';
import { insertNotification } from '$lib/notify';

export const PATCH: RequestHandler = async ({ locals, params, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (!['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'].includes(locals.dealer.role)) {
    return errorResponse('Insufficient permissions', 403);
  }

  const db = getDb({ platform });
  const agent = await db.prepare("SELECT * FROM dealers WHERE id = ? AND role = 'AGENT'").bind(params.id).first<Record<string, any>>();
  if (!agent) return errorResponse('Agent not found', 404);

  // Scope: agency admins only their agency; branch admins only their branch.
  if (locals.dealer.role === 'AGENCY_OWNER' && String(agent.agency_id) !== locals.dealer.agencyId) {
    return errorResponse('Agent is outside your agency', 403);
  }
  if (locals.dealer.role === 'BRANCH_ADMIN' && String(agent.branch_id) !== locals.dealer.branchId) {
    return errorResponse('Agent is outside your branch', 403);
  }

  const body = await request.json().catch(() => null) as Record<string, unknown> | null;
  if (!body) return errorResponse('Invalid JSON', 400);

  const name = body.name != null ? String(body.name).trim() : String(agent.name);
  const phone = body.phone !== undefined ? (String(body.phone ?? '').trim() || null) : (agent.phone as string | null);
  let branchId = agent.branch_id as string | null;
  let agencyId = agent.agency_id as string | null;
  const isApproved = body.isApproved !== undefined ? (body.isApproved ? 1 : 0) : Number(agent.is_approved);

  if (body.name != null && (name.length < 2 || name.length > 120)) {
    return errorResponse('Agent name must be 2-120 characters', 400);
  }
  if (body.branchId != null && String(body.branchId).trim() !== String(branchId ?? '')) {
    const targetBranch = String(body.branchId).trim();
    const branch = await db.prepare('SELECT id, agency_id FROM branches WHERE id = ? AND is_active = 1').bind(targetBranch).first<{ id: string; agency_id: string }>();
    if (!branch) return errorResponse('Target branch not found or inactive', 404);
    if (locals.dealer.role === 'AGENCY_OWNER' && branch.agency_id !== locals.dealer.agencyId) {
      return errorResponse('Target branch is outside your agency', 403);
    }
    if (locals.dealer.role === 'BRANCH_ADMIN' && targetBranch !== locals.dealer.branchId) {
      return errorResponse('Branch admins can only assign agents to their own branch', 403);
    }
    branchId = branch.id;
    agencyId = branch.agency_id;
  }

  const now = Math.floor(Date.now() / 1000);
  await db.prepare('UPDATE dealers SET name = ?, phone = ?, branch_id = ?, agency_id = ?, is_approved = ?, approved_at = CASE WHEN ? = 1 THEN COALESCE(approved_at, ?) ELSE approved_at END WHERE id = ?')
    .bind(name, phone, branchId, agencyId, isApproved, isApproved, now, params.id).run();

  const banned = isApproved === 0;
  await logActivity(db, {
    actor: locals.dealer,
    action: banned ? 'AGENT_BANNED' : (body.isApproved !== undefined ? 'AGENT_UNBANNED' : 'AGENT_UPDATED'),
    details: banned
      ? `Banned agent ${name} (${agent.email}) — access revoked immediately`
      : `Updated agent ${name} (${agent.email})${body.isApproved === true ? ' — reinstated' : ''}`
  });

  // Notify the agent about the ban/reinstate in their (blocked or restored) feed.
  if (body.isApproved !== undefined) {
    try {
      await insertNotification(db, {
        recipientId: params.id,
        type: banned ? 'ACCOUNT_BANNED' : 'ACCOUNT_REINSTATED',
        title: banned ? 'Account suspended' : 'Account reinstated',
        message: banned
          ? 'Your agent account has been suspended by your administrator. Please contact support.'
          : 'Your agent account has been reinstated. Welcome back!'
      });
    } catch { /* notification is best-effort */ }
  }

  return json({ id: params.id, name, phone, branchId, agencyId, isApproved: isApproved === 1 });
};

export const DELETE: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (!['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'].includes(locals.dealer.role)) {
    return errorResponse('Insufficient permissions', 403);
  }

  const db = getDb({ platform });
  const agent = await db.prepare("SELECT * FROM dealers WHERE id = ? AND role = 'AGENT'").bind(params.id).first<Record<string, any>>();
  if (!agent) return errorResponse('Agent not found', 404);
  if (locals.dealer.role === 'AGENCY_OWNER' && String(agent.agency_id) !== locals.dealer.agencyId) {
    return errorResponse('Agent is outside your agency', 403);
  }
  if (locals.dealer.role === 'BRANCH_ADMIN' && String(agent.branch_id) !== locals.dealer.branchId) {
    return errorResponse('Agent is outside your branch', 403);
  }

  const accountCount = await db.prepare('SELECT COUNT(*) as c FROM accounts WHERE enrolled_by = ?').bind(params.id).first<{ c: number }>();
  if ((accountCount?.c ?? 0) > 0) {
    return errorResponse(`Cannot delete: this agent has ${accountCount?.c} enrolled customer(s). Ban the agent instead, or reassign their customers first.`, 409);
  }

  const deviceCount = await db.prepare('SELECT COUNT(*) as c FROM devices WHERE assigned_to = ?').bind(params.id).first<{ c: number }>();
  if ((deviceCount?.c ?? 0) > 0) {
    return errorResponse(`Cannot delete: ${deviceCount?.c} device(s) are still assigned to this agent. Unassign them first.`, 409);
  }

  await db.batch([
    db.prepare('DELETE FROM notifications WHERE recipient_id = ?').bind(params.id),
    db.prepare('DELETE FROM sessions WHERE dealer_id = ?').bind(params.id),
    db.prepare('DELETE FROM dealers WHERE id = ?').bind(params.id)
  ]);

  await logActivity(db, {
    actor: locals.dealer,
    action: 'AGENT_DELETED',
    details: `Deleted agent ${String(agent.name)} (${String(agent.email)}) — no customers or assigned devices`
  });

  return json({ success: true, id: params.id });
};
