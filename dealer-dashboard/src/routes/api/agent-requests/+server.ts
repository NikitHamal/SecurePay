import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });

  let query: string;
  let params: any[];

  switch (locals.dealer.role) {
    case 'SUPER_ADMIN':
      query = `SELECT ar.id, ar.full_name, ar.email, ar.phone, ar.status, ar.requested_branch_id, ar.created_at,
               b.name as branch_name
               FROM agent_requests ar
               LEFT JOIN branches b ON ar.requested_branch_id = b.id
               ORDER BY ar.created_at DESC`;
      params = [];
      break;
    case 'AGENCY_OWNER':
      query = `SELECT ar.id, ar.full_name, ar.email, ar.phone, ar.status, ar.requested_branch_id, ar.created_at,
               b.name as branch_name
               FROM agent_requests ar
               LEFT JOIN branches b ON ar.requested_branch_id = b.id
               WHERE b.agency_id = ?
               ORDER BY ar.created_at DESC`;
      params = [locals.dealer.agencyId];
      break;
    case 'BRANCH_ADMIN':
      query = `SELECT ar.id, ar.full_name, ar.email, ar.phone, ar.status, ar.requested_branch_id, ar.created_at,
               b.name as branch_name
               FROM agent_requests ar
               LEFT JOIN branches b ON ar.requested_branch_id = b.id
               WHERE ar.requested_branch_id = ?
               ORDER BY ar.created_at DESC`;
      params = [locals.dealer.branchId];
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
    branchName: r.branch_name,
    createdAt: Number(r.created_at) * 1000
  })));
};
