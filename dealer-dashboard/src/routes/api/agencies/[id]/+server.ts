import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { logActivity } from '$lib/audit';

export const PATCH: RequestHandler = async ({ locals, params, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });
  const agency = await db.prepare('SELECT * FROM agencies WHERE id = ?').bind(params.id).first<Record<string, any>>();
  if (!agency) return errorResponse('Agency not found', 404);

  // SUPER_ADMIN edits any agency; AGENCY_OWNER may edit only their own (not ownership).
  if (locals.dealer.role === 'SUPER_ADMIN') {
    // full access
  } else if (locals.dealer.role === 'AGENCY_OWNER') {
    if (params.id !== locals.dealer.agencyId) return errorResponse('You can only edit your own agency', 403);
  } else {
    return errorResponse('Insufficient permissions', 403);
  }

  const body = await request.json().catch(() => null) as Record<string, unknown> | null;
  if (!body) return errorResponse('Invalid JSON', 400);

  const name = body.name != null ? String(body.name).trim() : String(agency.name);
  const phone = body.phone !== undefined ? (String(body.phone ?? '').trim() || null) : (agency.phone as string | null);
  const region = body.region !== undefined ? (String(body.region ?? '').trim() || null) : (agency.region as string | null);
  const isActive = body.isActive !== undefined ? (body.isActive ? 1 : 0) : Number(agency.is_active);
  let ownerId = agency.owner_id as string;

  if (body.name != null && (name.length < 2 || name.length > 120)) {
    return errorResponse('Agency name must be 2-120 characters', 400);
  }
  if (body.ownerId != null) {
    if (locals.dealer.role !== 'SUPER_ADMIN') return errorResponse('Only Super Admin can change agency ownership', 403);
    const newOwner = String(body.ownerId).trim();
    const owner = await db.prepare("SELECT id FROM dealers WHERE id = ? AND role IN ('SUPER_ADMIN','AGENCY_OWNER')").bind(newOwner).first();
    if (!owner) return errorResponse('Owner must be an existing SUPER_ADMIN or AGENCY_OWNER', 400);
    ownerId = newOwner;
  }

  await db.prepare('UPDATE agencies SET name = ?, phone = ?, region = ?, is_active = ?, owner_id = ? WHERE id = ?')
    .bind(name, phone, region, isActive, ownerId, params.id).run();

  await logActivity(db, {
    actor: locals.dealer,
    action: 'AGENCY_UPDATED',
    details: `Updated agency ${name} (${params.id})${body.ownerId != null ? ' — owner changed' : ''}${body.isActive !== undefined ? ` — ${isActive ? 'activated' : 'deactivated'}` : ''}`
  });

  return json({ id: params.id, name, phone, region, isActive: isActive === 1, ownerId });
};

export const DELETE: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (locals.dealer.role !== 'SUPER_ADMIN') return errorResponse('Only Super Admin can delete agencies', 403);

  const db = getDb({ platform });
  const agency = await db.prepare('SELECT id, name FROM agencies WHERE id = ?').bind(params.id).first<{ id: string; name: string }>();
  if (!agency) return errorResponse('Agency not found', 404);

  const branchCount = await db.prepare('SELECT COUNT(*) as c FROM branches WHERE agency_id = ?').bind(params.id).first<{ c: number }>();
  const dealerCount = await db.prepare('SELECT COUNT(*) as c FROM dealers WHERE agency_id = ?').bind(params.id).first<{ c: number }>();
  if ((branchCount?.c ?? 0) > 0 || (dealerCount?.c ?? 0) > 0) {
    return errorResponse(`Cannot delete: ${agency.name} still has ${branchCount?.c ?? 0} branch(es) and ${dealerCount?.c ?? 0} staff member(s). Remove or reassign them first, or deactivate the agency instead.`, 409);
  }

  await db.prepare('DELETE FROM agencies WHERE id = ?').bind(params.id).run();
  await logActivity(db, { actor: locals.dealer, action: 'AGENCY_DELETED', details: `Deleted empty agency ${agency.name} (${params.id})` });
  return json({ success: true, id: params.id });
};
