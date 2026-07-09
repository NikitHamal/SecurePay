import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse, releaseFields } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  if (locals.dealer.role !== 'AGENT') {
    return errorResponse('This endpoint is for agents only', 403);
  }

  const db = getDb({ platform });
  const result = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.enrolled_by = ?
    ORDER BY a.created_at DESC
  `).bind(locals.dealer.id).all();

  const sales = result.results.map((row) => {
    const nextPaymentDue = Number(row.next_payment_due);
    const status = row.release_approved === 1
      ? 'ACTIVE'
      : (row.is_stolen === 1 ? 'STOLEN' : (row.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(nextPaymentDue)));

    return {
      id: row.id,
      customerName: row.customer_name,
      imei: row.imei,
      deviceModel: row.device_model,
      planName: row.plan_name,
      totalLoanAmount: Number(row.total_loan_amount),
      amountPaid: Number(row.amount_paid),
      remainingBalance: Math.max(0, Number(row.total_loan_amount) - Number(row.amount_paid)),
      dailyRate: Number(row.daily_rate),
      nextPaymentDueEpochMillis: nextPaymentDue,
      status,
      downPayment: Number(row.down_payment),
      ...releaseFields(row as Record<string, any>)
    };
  });

  return json(sales);
};
