import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { db } from '$lib/db';

export const GET: RequestHandler = async () => {
  const result = await db.execute('SELECT * FROM plans ORDER BY term_days ASC');

  const plans = result.rows.map((row) => ({
    id: row.id as string,
    name: row.name as string,
    termDays: Number(row.term_days),
    totalAmount: Number(row.total_amount),
    dailyRate: Number(row.daily_rate),
    minDownPayment: Number(row.min_down_payment)
  }));

  return json(plans);
};