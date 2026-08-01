import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse, releaseApproved } from '$lib/api/server';
import { normalizeNationalId, NATIONAL_ID_NORM_SQL } from '$lib/audit';

/**
 * GET /api/accounts/check-id?nationalId=GHA-XXXXXXXXX-X[&exclude=<accountId>]
 *
 * Dealer-authenticated duplicate check used by the enrollment wizard to give
 * the agent instant feedback while typing an ID number. The lookup is company
 * wide on purpose: a Ghana Card identifies a person, and a person must never
 * be enrolled twice — even by a different agent, branch or agency.
 *
 * Only the minimal context needed to act on the result is returned.
 */
export const GET: RequestHandler = async ({ locals, url, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const nationalId = String(url.searchParams.get('nationalId') ?? '').trim();
  const exclude = String(url.searchParams.get('exclude') ?? '').trim();
  if (nationalId.length < 4 || nationalId.length > 64) {
    return errorResponse('nationalId must be between 4 and 64 characters', 400);
  }
  const normalized = normalizeNationalId(nationalId);
  if (!normalized) return json({ duplicate: false, matches: [] });

  const db = getDb({ platform });
  const result = await db.prepare(`
    SELECT a.id, a.customer_name, a.amount_paid, a.total_loan_amount, a.created_at,
           COALESCE(a.release_approved, 0) AS release_approved,
           d.model AS device_model, dl.name AS enrolled_by_name
      FROM accounts a
      JOIN devices d ON d.id = a.device_id
      LEFT JOIN dealers dl ON dl.id = a.enrolled_by
     WHERE ${NATIONAL_ID_NORM_SQL} = ?
       ${exclude ? 'AND a.id != ?' : ''}
     ORDER BY a.created_at DESC
     LIMIT 3
  `).bind(...(exclude ? [normalized, exclude] : [normalized])).all<{
    id: string;
    customer_name: string;
    amount_paid: number;
    total_loan_amount: number;
    created_at: number;
    release_approved: number;
    device_model: string;
    enrolled_by_name: string | null;
  }>();

  const matches = result.results.map((row) => ({
    accountId: row.id,
    customerName: row.customer_name,
    deviceModel: row.device_model,
    enrolledByName: row.enrolled_by_name ?? null,
    createdAt: Number(row.created_at) * 1000,
    outstandingBalance: Math.max(0, Number(row.total_loan_amount) - Number(row.amount_paid)),
    fullyPaid: releaseApproved(row as unknown as Record<string, unknown>)
  }));

  return json({ duplicate: matches.length > 0, matches });
};
