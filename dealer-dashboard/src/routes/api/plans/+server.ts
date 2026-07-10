import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });
  const result = await db.prepare('SELECT * FROM plans ORDER BY term_days ASC').all();

  const plans = result.results.map((row) => ({
    id: row.id as string,
    name: row.name as string,
    termDays: Number(row.term_days),
    totalAmount: Number(row.total_amount),
    dailyRate: Number(row.daily_rate),
    minDownPayment: Number(row.min_down_payment)
  }));

  return json(plans, { headers: { 'Cache-Control': 'private, no-store' } });
};
