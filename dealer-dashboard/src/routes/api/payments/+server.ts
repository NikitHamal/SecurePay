import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';
import type { Customer, Status } from '$lib/types';

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const body = await request.json();
  const { accountId, amount, method, reference } = body;

  if (!accountId || !amount || !method) {
    return errorResponse('accountId, amount, and method are required', 400);
  }

  const db = getDb({ platform });

  const account = await db.prepare('SELECT * FROM accounts WHERE id = ? AND dealer_id = ?').bind(accountId, locals.dealer.id).first();

  if (!account) {
    return errorResponse('Account not found', 404);
  }

  const newAmountPaid = Number(account.amount_paid) + Number(amount);
  const dailyRate = Number(account.daily_rate);
  const currentDue = Number(account.next_payment_due);
  const now = Date.now();

  const daysExtended = Math.floor(Number(amount) / dailyRate);
  const base = Math.max(currentDue, now);
  const newDue = daysExtended > 0 ? base + daysExtended * 24 * 60 * 60 * 1000 : currentDue;

  const paymentId = uuidv4();
  await db.prepare(
    `INSERT INTO payments (id, account_id, amount, method, reference, recorded_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)`
  ).bind(paymentId, accountId, Number(amount), method, reference || null, locals.dealer.id, Math.floor(now / 1000)).run();

  await db.prepare(
    'UPDATE accounts SET amount_paid = ?, next_payment_due = ?, locked_by_dealer = 0, updated_at = ? WHERE id = ?'
  ).bind(newAmountPaid, newDue, Math.floor(now / 1000), accountId).run();

  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, p.name as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ?
  `).bind(accountId).first();

  const nextDue = Number(row!.next_payment_due);
  const amtPaid = Number(row!.amount_paid);
  const totalLoan = Number(row!.total_loan_amount);
  const status: Status = row!.locked_by_dealer === 1
    ? 'LOCKED'
    : computeStatus(nextDue);

  const customer: Customer = {
    id: row!.id as string,
    customerName: row!.customer_name as string,
    nationalId: row!.national_id as string,
    phoneNumber: row!.phone_number as string,
    imei: row!.imei as string,
    deviceModel: row!.device_model as string,
    planName: row!.plan_name as string,
    totalLoanAmount: totalLoan,
    amountPaid: amtPaid,
    remainingBalance: Math.max(0, totalLoan - amtPaid),
    dailyRate: Number(row!.daily_rate),
    nextPaymentDueEpochMillis: nextDue,
    status
  };

  return json({ payment: { id: paymentId, accountId, amount, method, reference }, account: customer });
};