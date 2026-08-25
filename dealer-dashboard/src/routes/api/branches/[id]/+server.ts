import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { logActivity } from '$lib/audit';

export const PATCH: RequestHandler = async ({ locals, params, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });
  const branch = await db.prepare('SELECT * FROM branches WHERE id = ?').bind(params.id).first<Record<string, any>>();
  if (!branch) return errorResponse('Branch not found', 404);

  // SUPER_ADMIN edits any branch; AGENCY_OWNER branches in own agency; BRANCH_ADMIN own branch.
  if (locals.dealer.role === 'SUPER_ADMIN') {
    // full access
  } else if (locals.dealer.role === 'AGENCY_OWNER') {
    if (String(branch.agency_id) !== locals.dealer.agencyId) return errorResponse('Branch is outside your agency', 403);
  } else if (locals.dealer.role === 'BRANCH_ADMIN') {
    if (params.id !== locals.dealer.branchId) return errorResponse('You can only edit your own branch', 403);
  } else {
    return errorResponse('Insufficient permissions', 403);
  }

  const body = await request.json().catch(() => null) as Record<string, unknown> | null;
  if (!body) return errorResponse('Invalid JSON', 400);

  const name = body.name != null ? String(body.name).trim() : String(branch.name);
  const address = body.address !== undefined ? (String(body.address ?? '').trim() || null) : (branch.address as string | null);
  const phone = body.phone !== undefined ? (String(body.phone ?? '').trim() || null) : (branch.phone as string | null);
  const isActive = body.isActive !== undefined ? (body.isActive ? 1 : 0) : Number(branch.is_active);
  let agencyId = String(branch.agency_id);

  if (body.name != null && (name.length < 2 || name.length > 120)) {
    return errorResponse('Branch name must be 2-120 characters', 400);
  }
  if (body.agencyId != null && String(body.agencyId).trim() !== agencyId) {
    if (locals.dealer.role !== 'SUPER_ADMIN') return errorResponse('Only Super Admin can move a branch to another agency', 403);
    const targetAgency = String(body.agencyId).trim();
    const agency = await db.prepare('SELECT id FROM agencies WHERE id = ? AND is_active = 1').bind(targetAgency).first();
    if (!agency) return errorResponse('Target agency not found or inactive', 404);
    agencyId = targetAgency;
  }
  if (body.adminId !== undefined) {
    if (locals.dealer.role !== 'SUPER_ADMIN' && locals.dealer.role !== 'AGENCY_OWNER') {
      return errorResponse('Only Super Admin or Agency Owner can set the branch admin', 403);
    }
    const adminId = String(body.adminId ?? '').trim() || null;
    if (adminId) {
      const admin = await db.prepare("SELECT id FROM dealers WHERE id = ? AND role IN ('SUPER_ADMIN','AGENCY_OWNER','BRANCH_ADMIN')").bind(adminId).first();
      if (!admin) return errorResponse('Branch admin must be an existing admin-role staff member', 400);
    }
    await db.prepare('UPDATE branches SET admin_id = ? WHERE id = ?').bind(adminId, params.id).run();
  }

  await db.prepare('UPDATE branches SET name = ?, address = ?, phone = ?, is_active = ?, agency_id = ? WHERE id = ?')
    .bind(name, address, phone, isActive, agencyId, params.id).run();

  await logActivity(db, {
    actor: locals.dealer,
    action: 'BRANCH_UPDATED',
    details: `Updated branch ${name} (${params.id})${body.isActive !== undefined ? ` — ${isActive ? 'activated' : 'deactivated'}` : ''}`
  });

  return json({ id: params.id, name, address, phone, isActive: isActive === 1, agencyId });
};

export const DELETE: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (!['SUPER_ADMIN', 'AGENCY_OWNER'].includes(locals.dealer.role)) {
    return errorResponse('Insufficient permissions', 403);
  }

  const db = getDb({ platform });
  const branch = await db.prepare('SELECT id, name, agency_id FROM branches WHERE id = ?').bind(params.id).first<{ id: string; name: string; agency_id: string }>();
  if (!branch) return errorResponse('Branch not found', 404);
  if (locals.dealer.role === 'AGENCY_OWNER' && String(branch.agency_id) !== locals.dealer.agencyId) {
    return errorResponse('Branch is outside your agency', 403);
  }

  const agentCount = await db.prepare("SELECT COUNT(*) as c FROM dealers WHERE branch_id = ? AND role = 'AGENT'").bind(params.id).first<{ c: number }>();
  if ((agentCount?.c ?? 0) > 0) {
    return errorResponse(`Cannot delete: ${agentCount?.c} agent(s) are still assigned to ${branch.name}. Reassign them to another branch first, or deactivate the branch instead.`, 409);
  }

  await db.batch([
    db.prepare('UPDATE accounts SET branch_id = NULL WHERE branch_id = ?').bind(params.id),
    db.prepare('DELETE FROM branches WHERE id = ?').bind(params.id)
  ]);

  await logActivity(db, { actor: locals.dealer, action: 'BRANCH_DELETED', details: `Deleted branch ${branch.name} (${params.id})` });
  return json({ success: true, id: params.id });
};
