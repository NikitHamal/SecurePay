import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  if (!['SUPER_ADMIN', 'AGENCY_OWNER'].includes(locals.dealer.role)) {
    return errorResponse('Insufficient permissions', 403);
  }

  const db = getDb({ platform });

  let query: string;
  let params: string[];

  if (locals.dealer.role === 'SUPER_ADMIN') {
    query = `SELECT a.*, d.name as owner_name,
             (SELECT COUNT(*) FROM branches WHERE agency_id = a.id) as branch_count,
             (SELECT COUNT(*) FROM dealers WHERE agency_id = a.id AND role = 'AGENT') as agent_count
             FROM agencies a
             LEFT JOIN dealers d ON a.owner_id = d.id
             WHERE a.is_active = 1
             ORDER BY a.created_at DESC`;
    params = [];
  } else {
    query = `SELECT a.*, d.name as owner_name,
             (SELECT COUNT(*) FROM branches WHERE agency_id = a.id) as branch_count,
             (SELECT COUNT(*) FROM dealers WHERE agency_id = a.id AND role = 'AGENT') as agent_count
             FROM agencies a
             LEFT JOIN dealers d ON a.owner_id = d.id
             WHERE a.id = ? AND a.is_active = 1`;
    params = [locals.dealer.agencyId || ''];
  }

  const result = await db.prepare(query).bind(...params).all();
  return json(result.results.map(r => ({
    id: r.id,
    name: r.name,
    ownerId: r.owner_id,
    ownerName: r.owner_name,
    phone: r.phone,
    region: r.region,
    branchCount: Number(r.branch_count),
    agentCount: Number(r.agent_count),
    isActive: r.is_active === 1,
    createdAt: Number(r.created_at) * 1000
  })));
};

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  if (locals.dealer.role !== 'SUPER_ADMIN') {
    return errorResponse('Only Super Admin can create agencies', 403);
  }

  const { name, phone, region } = await request.json();
  if (!name) return errorResponse('Agency name is required', 400);

  const db = getDb({ platform });
  const agencyId = `AGY-${uuidv4().slice(0, 8).toUpperCase()}`;

  await db.prepare(`
    INSERT INTO agencies (id, name, owner_id, phone, region, is_active, created_at)
    VALUES (?, ?, ?, ?, ?, 1, ?)
  `).bind(agencyId, name, locals.dealer.id, phone || null, region || null, Math.floor(Date.now() / 1000)).run();

  return json({ id: agencyId, name, phone, region }, { status: 201 });
};
