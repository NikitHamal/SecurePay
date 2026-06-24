import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse, releaseFields, releaseApproved } from '$lib/api/server';
import type { Customer, Status } from '$lib/types';

export const GET: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const db = getDb({ platform });
  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ? AND a.dealer_id = ?
  `).bind(params.id, locals.dealer.id).first();

  if (!row) {
    return errorResponse('Account not found', 404);
  }

  const nextPaymentDue = Number(row.next_payment_due);
  const amountPaid = Number(row.amount_paid);
  const totalLoanAmount = Number(row.total_loan_amount);
  const status: Status = releaseApproved(row as Record<string, unknown>)
    ? 'ACTIVE'
    : (row.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(nextPaymentDue));

  const customer: Customer = {
    id: row.id as string,
    customerName: row.customer_name as string,
    nationalId: row.national_id as string,
    phoneNumber: row.phone_number as string,
    imei: row.imei as string,
    deviceModel: row.device_model as string,
    planName: row.plan_name as string || 'Custom',
    totalLoanAmount,
    amountPaid,
    remainingBalance: Math.max(0, totalLoanAmount - amountPaid),
    dailyRate: Number(row.daily_rate),
    nextPaymentDueEpochMillis: nextPaymentDue,
    status,
    ...releaseFields(row as Record<string, unknown>)
  };

  return json(customer);
};

export const PATCH: RequestHandler = async ({ locals, params, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const body = await request.json();
  const { customerName, nationalId, phoneNumber, dailyRate, totalLoanAmount, termDays, nextPaymentDue, amountPaid } = body;

  const updates: string[] = [];
  const args: (string | number)[] = [];

  if (customerName !== undefined) { updates.push('customer_name = ?'); args.push(customerName); }
  if (nationalId !== undefined) { updates.push('national_id = ?'); args.push(nationalId); }
  if (phoneNumber !== undefined) { updates.push('phone_number = ?'); args.push(phoneNumber); }
  if (dailyRate !== undefined) { updates.push('daily_rate = ?'); args.push(dailyRate); }
  if (totalLoanAmount !== undefined) { updates.push('total_loan_amount = ?'); args.push(totalLoanAmount); }
  if (termDays !== undefined) { updates.push('term_days = ?'); args.push(termDays); }
  if (nextPaymentDue !== undefined) { updates.push('next_payment_due = ?'); args.push(nextPaymentDue); }
  if (amountPaid !== undefined) { updates.push('amount_paid = ?'); args.push(amountPaid); }

  if (updates.length === 0) {
    return errorResponse('No fields to update', 400);
  }

  updates.push('updated_at = ?');
  args.push(Math.floor(Date.now() / 1000));
  args.push(params.id);
  args.push(locals.dealer.id);

  const db = getDb({ platform });
  await db.prepare(`UPDATE accounts SET ${updates.join(', ')} WHERE id = ? AND dealer_id = ?`).bind(...args).run();

  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ? AND a.dealer_id = ?
  `).bind(params.id, locals.dealer.id).first();

  const nextDue = Number(row!.next_payment_due);
  const amtPaid = Number(row!.amount_paid);
  const totalLoan = Number(row!.total_loan_amount);
  const status: Status = releaseApproved(row as Record<string, unknown>)
    ? 'ACTIVE'
    : (row!.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(nextDue));

  const customer: Customer = {
    id: row!.id as string,
    customerName: row!.customer_name as string,
    nationalId: row!.national_id as string,
    phoneNumber: row!.phone_number as string,
    imei: row!.imei as string,
    deviceModel: row!.device_model as string,
    planName: row!.plan_name as string || 'Custom',
    totalLoanAmount: totalLoan,
    amountPaid: amtPaid,
    remainingBalance: Math.max(0, totalLoan - amtPaid),
    dailyRate: Number(row!.daily_rate),
    nextPaymentDueEpochMillis: nextDue,
    status,
    ...releaseFields(row as Record<string, unknown>)
  };

  return json(customer);
};
export const DELETE: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const db = getDb({ platform });
  const row = await db.prepare(`
    SELECT id, device_id
    FROM accounts
    WHERE id = ? AND dealer_id = ?
  `).bind(params.id, locals.dealer.id).first<{ id: string; device_id: string }>();

  if (!row) {
    return errorResponse('Account not found', 404);
  }

  await db.batch([
    db.prepare('DELETE FROM provisioning_tokens WHERE account_id = ?').bind(params.id),
    db.prepare('DELETE FROM payments WHERE account_id = ?').bind(params.id),
    db.prepare('DELETE FROM lock_events WHERE account_id = ?').bind(params.id),
    db.prepare('DELETE FROM accounts WHERE id = ? AND dealer_id = ?').bind(params.id, locals.dealer.id),
    db.prepare('UPDATE devices SET status = ? WHERE id = ? AND dealer_id = ?').bind('in_stock', row.device_id, locals.dealer.id)
  ]);

  return json({ success: true, id: params.id, deviceId: row.device_id });
};
