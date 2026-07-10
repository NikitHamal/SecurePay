import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });

  let query: string;
  let params: string[];

  if (locals.dealer.role === 'SUPER_ADMIN') {
    query = `SELECT b.*, a.name as agency_name, d.name as admin_name,
             (SELECT COUNT(*) FROM dealers WHERE branch_id = b.id AND role = 'AGENT') as agent_count
             FROM branches b
             LEFT JOIN agencies a ON b.agency_id = a.id
             LEFT JOIN dealers d ON b.admin_id = d.id
             WHERE b.is_active = 1
             ORDER BY b.created_at DESC`;
    params = [];
  } else if (locals.dealer.role === 'AGENCY_OWNER') {
    query = `SELECT b.*, a.name as agency_name, d.name as admin_name,
             (SELECT COUNT(*) FROM dealers WHERE branch_id = b.id AND role = 'AGENT') as agent_count
             FROM branches b
             LEFT JOIN agencies a ON b.agency_id = a.id
             LEFT JOIN dealers d ON b.admin_id = d.id
             WHERE b.agency_id = ? AND b.is_active = 1
             ORDER BY b.created_at DESC`;
    params = [locals.dealer.agencyId || ''];
  } else if (locals.dealer.role === 'BRANCH_ADMIN') {
    query = `SELECT b.*, a.name as agency_name, d.name as admin_name,
             (SELECT COUNT(*) FROM dealers WHERE branch_id = b.id AND role = 'AGENT') as agent_count
             FROM branches b
             LEFT JOIN agencies a ON b.agency_id = a.id
             LEFT JOIN dealers d ON b.admin_id = d.id
             WHERE b.id = ? AND b.is_active = 1`;
    params = [locals.dealer.branchId || ''];
  } else {
    return errorResponse('Insufficient permissions', 403);
  }

  const result = await db.prepare(query).bind(...params).all();
  return json(result.results.map(r => ({
    id: r.id,
    name: r.name,
    agencyId: r.agency_id,
    agencyName: r.agency_name,
    adminId: r.admin_id,
    adminName: r.admin_name,
    address: r.address,
    phone: r.phone,
    agentCount: Number(r.agent_count),
    isActive: r.is_active === 1,
    createdAt: Number(r.created_at) * 1000
  })));
};

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  if (!['SUPER_ADMIN', 'AGENCY_OWNER'].includes(locals.dealer.role)) {
    return errorResponse('Insufficient permissions', 403);
  }

  const { name, address, phone, agencyId } = await request.json();
  if (!name) return errorResponse('Branch name is required', 400);

  const db = getDb({ platform });
  const branchAgencyId = String(agencyId || locals.dealer.agencyId || '').trim();
  if (!branchAgencyId) return errorResponse('Agency ID is required', 400);
  if (locals.dealer.role === 'AGENCY_OWNER' && branchAgencyId !== locals.dealer.agencyId) {
    return errorResponse('Cannot create a branch outside your agency', 403);
  }
  const agency = await db.prepare('SELECT id FROM agencies WHERE id = ? AND is_active = 1')
    .bind(branchAgencyId).first();
  if (!agency) return errorResponse('Agency not found', 404);

  const branchId = `BR-${uuidv4().slice(0, 8).toUpperCase()}`;

  await db.prepare(`
    INSERT INTO branches (id, name, agency_id, address, phone, is_active, created_at)
    VALUES (?, ?, ?, ?, ?, 1, ?)
  `).bind(branchId, name, branchAgencyId, address || null, phone || null, Math.floor(Date.now() / 1000)).run();

  return json({ id: branchId, name, agencyId: branchAgencyId, address, phone }, { status: 201 });
};
