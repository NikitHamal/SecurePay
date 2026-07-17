import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals, platform, url }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });
  const statusFilter = url.searchParams.get('status')?.toUpperCase();
  const allowedStatuses = new Set(['PENDING', 'APPROVED', 'REJECTED']);

  let query: string;
  let params: any[] = [];
  let statusClause = '';

  if (statusFilter && allowedStatuses.has(statusFilter)) {
    statusClause = ' AND ar.status = ?';
    params.push(statusFilter);
  }

  switch (locals.dealer.role) {
    case 'SUPER_ADMIN':
      query = `SELECT ar.id, ar.full_name, ar.email, ar.phone, ar.status, ar.requested_branch_id, ar.created_at,
               b.name as branch_name
               FROM agent_requests ar
               LEFT JOIN branches b ON ar.requested_branch_id = b.id
               WHERE 1=1${statusClause}
               ORDER BY ar.created_at DESC`;
      break;
    case 'AGENCY_OWNER':
      query = `SELECT ar.id, ar.full_name, ar.email, ar.phone, ar.status, ar.requested_branch_id, ar.created_at,
               b.name as branch_name
               FROM agent_requests ar
               LEFT JOIN branches b ON ar.requested_branch_id = b.id
               WHERE b.agency_id = ?${statusClause}
               ORDER BY ar.created_at DESC`;
      params = [locals.dealer.agencyId, ...params.slice(1)];
      if (statusFilter && allowedStatuses.has(statusFilter)) {
        params = [locals.dealer.agencyId, statusFilter];
      } else {
        params = [locals.dealer.agencyId];
      }
      break;
    case 'BRANCH_ADMIN':
      query = `SELECT ar.id, ar.full_name, ar.email, ar.phone, ar.status, ar.requested_branch_id, ar.created_at,
               b.name as branch_name
               FROM agent_requests ar
               LEFT JOIN branches b ON ar.requested_branch_id = b.id
               WHERE ar.requested_branch_id = ?${statusClause}
               ORDER BY ar.created_at DESC`;
      if (statusFilter && allowedStatuses.has(statusFilter)) {
        params = [locals.dealer.branchId, statusFilter];
      } else {
        params = [locals.dealer.branchId];
      }
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
