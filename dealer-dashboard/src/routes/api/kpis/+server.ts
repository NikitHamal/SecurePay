import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { db } from '$lib/db';
import { computeStatus, errorResponse } from '$lib/api/server';
import type { KpiSummary } from '$lib/types';

export const GET: RequestHandler = async ({ locals }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const dealerId = locals.dealer.id;

  const accountsResult = await db.execute({
    sql: `
      SELECT a.*, d.imei, d.model as device_model, p.name as plan_name
      FROM accounts a
      JOIN devices d ON a.device_id = d.id
      JOIN plans p ON a.plan_id = p.id
      WHERE a.dealer_id = ?
    `,
    args: [dealerId]
  });

  const now = Date.now();
  let activeNodes = 0;
  let lockedCount = 0;
  let warningCount = 0;
  let totalOutstanding = 0;
  let collectedToday = 0;

  for (const row of accountsResult.rows) {
    const status = row.locked_by_dealer === 1
      ? 'LOCKED' as const
      : computeStatus(Number(row.next_payment_due), now);

    if (status === 'ACTIVE') {
      activeNodes += 1;
      collectedToday += Number(row.daily_rate);
    } else if (status === 'WARNING') {
      warningCount += 1;
      collectedToday += Number(row.daily_rate);
    } else {
      lockedCount += 1;
    }

    totalOutstanding += Math.max(0, Number(row.total_loan_amount) - Number(row.amount_paid));
  }

  const summary: KpiSummary = {
    activeNodes,
    lockedCount,
    warningCount,
    totalOutstanding,
    collectedToday
  };

  return json(summary);
};