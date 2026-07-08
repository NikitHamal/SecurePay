import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals, platform, url }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });
  const status = url.searchParams.get('status') || 'PENDING';

  let query: string;
  let params: string[];

  switch (locals.dealer.role) {
    case 'SUPER_ADMIN':
      query = `SELECT id, full_name, email, phone, status, requested_branch_id, created_at
               FROM agent_requests WHERE status = ? ORDER BY created_at DESC`;
      params = [status];
      break;
    case 'AGENCY_OWNER':
      query = `SELECT ar.id, ar.full_name, ar.email, ar.phone, ar.status, ar.requested_branch_id, ar.created_at
               FROM agent_requests ar
               LEFT JOIN branches b ON ar.requested_branch_id = b.id
               WHERE ar.status = ? AND (b.agency_id = ? OR ar.requested_branch_id IS NULL)
               ORDER BY ar.created_at DESC`;
      params = [status, locals.dealer.agencyId || ''];
      break;
    case 'BRANCH_ADMIN':
      query = `SELECT id, full_name, email, phone, status, requested_branch_id, created_at
               FROM agent_requests
               WHERE status = ? AND (requested_branch_id = ? OR requested_branch_id IS NULL)
               ORDER BY created_at DESC`;
      params = [status, locals.dealer.branchId || ''];
      break;
    default:
      return errorResponse('Insufficient permissions', 403);
  }

  const result = await db.prepare(query).bind(...params).all();
  return json(result.results.map(r => ({
    id: r.id,
    fullName: r.full_name,
    email: r.email,
    phone: r.phone,
    status: r.status,
    requestedBranchId: r.requested_branch_id,
    createdAt: Number(r.created_at) * 1000
  })));
};
