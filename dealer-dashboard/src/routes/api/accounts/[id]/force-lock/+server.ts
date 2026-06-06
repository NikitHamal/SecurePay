import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { db } from '$lib/db';
import { computeStatus, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';
import type { Customer, Status } from '$lib/types';

export const POST: RequestHandler = async ({ locals, params }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const accountId = params.id;

  const acctResult = await db.execute({
    sql: 'SELECT * FROM accounts WHERE id = ? AND dealer_id = ?',
    args: [accountId, locals.dealer.id]
  });

  if (acctResult.rows.length === 0) {
    return errorResponse('Account not found', 404);
  }

  const now = Date.now();
  const HOUR_MS = 60 * 60 * 1000;

  await db.execute({
    sql: 'UPDATE accounts SET locked_by_dealer = 1, next_payment_due = ?, updated_at = ? WHERE id = ?',
    args: [now - HOUR_MS, Math.floor(now / 1000), accountId]
  });

  await db.execute({
    sql: "INSERT INTO lock_events (id, account_id, event_type, triggered_by, created_at) VALUES (?, ?, 'lock', 'dealer', ?)",
    args: [uuidv4(), accountId, Math.floor(now / 1000)]
  });

  const result = await db.execute({
    sql: `
      SELECT a.*, d.imei, d.model as device_model, p.name as plan_name
      FROM accounts a
      JOIN devices d ON a.device_id = d.id
      JOIN plans p ON a.plan_id = p.id
      WHERE a.id = ?
    `,
    args: [accountId]
  });

  const row = result.rows[0];
  const nextDue = Number(row.next_payment_due);
  const amtPaid = Number(row.amount_paid);
  const totalLoan = Number(row.total_loan_amount);

  const customer: Customer = {
    id: row.id as string,
    customerName: row.customer_name as string,
    nationalId: row.national_id as string,
    phoneNumber: row.phone_number as string,
    imei: row.imei as string,
    deviceModel: row.device_model as string,
    planName: row.plan_name as string,
    totalLoanAmount: totalLoan,
    amountPaid: amtPaid,
    remainingBalance: Math.max(0, totalLoan - amtPaid),
    dailyRate: Number(row.daily_rate),
    nextPaymentDueEpochMillis: nextDue,
    status: 'LOCKED'
  };

  return json(customer);
};