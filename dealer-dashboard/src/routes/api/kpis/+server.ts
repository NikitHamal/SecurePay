import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const dealerId = locals.dealer.id;
  const db = getDb({ platform });
  const now = Date.now();

  const accountResult = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.dealer_id = ?
  `).bind(dealerId).all();

  let activeCount = 0;
  let lockedCount = 0;
  let warningCount = 0;
  let paidCount = 0;
  let totalOutstanding = 0;
  let totalAccounts = 0;

  for (const row of accountResult.results) {
    totalAccounts++;
    const status = row.locked_by_dealer === 1
      ? 'LOCKED' as const
      : computeStatus(Number(row.next_payment_due), now);

    if (status === 'ACTIVE') activeCount++;
    else if (status === 'WARNING') warningCount++;
    else lockedCount++;

    if (Number(row.amount_paid) >= Number(row.total_loan_amount)) paidCount++;

    totalOutstanding += Math.max(0, Number(row.total_loan_amount) - Number(row.amount_paid));
  }

  const todayStartSec = Math.floor(Math.floor(now / 1000) / 86400) * 86400;

  const payResult = await db.prepare(`
    SELECT COALESCE(SUM(p.amount), 0) as total
    FROM payments p
    JOIN accounts a ON p.account_id = a.id
    WHERE a.dealer_id = ? AND p.created_at >= ?
  `).bind(dealerId, todayStartSec).first<{ total: number }>();

  const collectedToday = Number(payResult?.total ?? 0);

  const sevenDaysAgo = todayStartSec - 6 * 86400;
  const dayRows = await db.prepare(`
    SELECT
      CAST(p.created_at / 86400 AS INTEGER) as day_num,
      COALESCE(SUM(p.amount), 0) as total
    FROM payments p
    JOIN accounts a ON p.account_id = a.id
    WHERE a.dealer_id = ? AND p.created_at >= ?
    GROUP BY day_num
    ORDER BY day_num
  `).bind(dealerId, sevenDaysAgo).all();

  const dayMap = new Map<number, number>();
  for (const row of dayRows.results) {
    dayMap.set(Number(row.day_num), Number(row.total));
  }

  const todayDayNum = Math.floor(todayStartSec / 86400);
  const collectionHistory: number[] = [];
  for (let i = 6; i >= 0; i--) {
    collectionHistory.push(dayMap.get(todayDayNum - i) ?? 0);
  }

  const outstandingHistory = new Array(7).fill(totalOutstanding);

  return json({
    activeNodes: activeCount,
    activeCount,
    lockedCount,
    warningCount,
    paidCount,
    totalOutstanding,
    collectedToday,
    totalAccounts,
    collectionHistory,
    outstandingHistory
  });
};