import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });

  let query: string;
  let params: string[];

  if (locals.dealer.role === 'SUPER_ADMIN') {
    query = `SELECT d.id, d.name, d.email, d.phone, d.branch_id, d.agency_id, d.is_approved, d.created_at,
             (SELECT COUNT(*) FROM accounts WHERE enrolled_by = d.id) as sales_count,
             (SELECT SUM(amount_paid) FROM accounts WHERE enrolled_by = d.id) as total_revenue,
             b.name as branch_name,
             a.name as agency_name
             FROM dealers d
             LEFT JOIN branches b ON d.branch_id = b.id
             LEFT JOIN agencies a ON d.agency_id = a.id
             WHERE d.role = 'AGENT'
             ORDER BY d.is_approved DESC, sales_count DESC`;
    params = [];
  } else if (locals.dealer.role === 'AGENCY_OWNER') {
    query = `SELECT d.id, d.name, d.email, d.phone, d.branch_id, d.agency_id, d.is_approved, d.created_at,
             (SELECT COUNT(*) FROM accounts WHERE enrolled_by = d.id) as sales_count,
             (SELECT SUM(amount_paid) FROM accounts WHERE enrolled_by = d.id) as total_revenue,
             b.name as branch_name,
             a.name as agency_name
             FROM dealers d
             LEFT JOIN branches b ON d.branch_id = b.id
             LEFT JOIN agencies a ON d.agency_id = a.id
             WHERE d.role = 'AGENT' AND d.agency_id = ?
             ORDER BY d.is_approved DESC, sales_count DESC`;
    params = [locals.dealer.agencyId || ''];
  } else if (locals.dealer.role === 'BRANCH_ADMIN') {
    query = `SELECT d.id, d.name, d.email, d.phone, d.branch_id, d.agency_id, d.is_approved, d.created_at,
             (SELECT COUNT(*) FROM accounts WHERE enrolled_by = d.id) as sales_count,
             (SELECT SUM(amount_paid) FROM accounts WHERE enrolled_by = d.id) as total_revenue,
             b.name as branch_name,
             a.name as agency_name
             FROM dealers d
             LEFT JOIN branches b ON d.branch_id = b.id
             LEFT JOIN agencies a ON d.agency_id = a.id
             WHERE d.role = 'AGENT' AND d.branch_id = ?
             ORDER BY d.is_approved DESC, sales_count DESC`;
    params = [locals.dealer.branchId || ''];
  } else {
    return errorResponse('Insufficient permissions', 403);
  }

  const result = await db.prepare(query).bind(...params).all();
  return json(result.results.map(r => ({
    id: r.id,
    name: r.name,
    email: r.email,
    phone: r.phone,
    branchId: r.branch_id,
    branchName: r.branch_name,
    agencyId: r.agency_id,
    agencyName: r.agency_name,
    isApproved: Number(r.is_approved) === 1,
    createdAt: Number(r.created_at) * 1000,
    salesCount: Number(r.sales_count),
    totalRevenue: Number(r.total_revenue || 0)
  })));
};
