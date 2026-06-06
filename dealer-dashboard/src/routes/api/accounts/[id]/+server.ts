import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { db } from '$lib/db';
import { computeStatus, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';
import type { Customer, Status } from '$lib/types';

export const GET: RequestHandler = async ({ locals, params }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const result = await db.execute({
    sql: `
      SELECT a.*, d.imei, d.model as device_model, p.name as plan_name
      FROM accounts a
      JOIN devices d ON a.device_id = d.id
      JOIN plans p ON a.plan_id = p.id
      WHERE a.id = ? AND a.dealer_id = ?
    `,
    args: [params.id, locals.dealer.id]
  });

  if (result.rows.length === 0) {
    return errorResponse('Account not found', 404);
  }

  const row = result.rows[0];
  const nextPaymentDue = Number(row.next_payment_due);
  const amountPaid = Number(row.amount_paid);
  const totalLoanAmount = Number(row.total_loan_amount);
  const status: Status = row.locked_by_dealer === 1
    ? 'LOCKED'
    : computeStatus(nextPaymentDue);

  const customer: Customer = {
    id: row.id as string,
    customerName: row.customer_name as string,
    nationalId: row.national_id as string,
    phoneNumber: row.phone_number as string,
    imei: row.imei as string,
    deviceModel: row.device_model as string,
    planName: row.plan_name as string,
    totalLoanAmount,
    amountPaid,
    remainingBalance: Math.max(0, totalLoanAmount - amountPaid),
    dailyRate: Number(row.daily_rate),
    nextPaymentDueEpochMillis: nextPaymentDue,
    status
  };

  return json(customer);
};

export const PATCH: RequestHandler = async ({ locals, params, request }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const body = await request.json();
  const { nextPaymentDue, amountPaid } = body;

  const updates: string[] = [];
  const args: (string | number)[] = [];

  if (nextPaymentDue !== undefined) {
    updates.push('next_payment_due = ?');
    args.push(nextPaymentDue);
  }
  if (amountPaid !== undefined) {
    updates.push('amount_paid = ?');
    args.push(amountPaid);
  }

  if (updates.length === 0) {
    return errorResponse('No fields to update', 400);
  }

  updates.push('updated_at = ?');
  args.push(Math.floor(Date.now() / 1000));
  args.push(params.id);
  args.push(locals.dealer.id);

  await db.execute({
    sql: `UPDATE accounts SET ${updates.join(', ')} WHERE id = ? AND dealer_id = ?`,
    args
  });

  const result = await db.execute({
    sql: `
      SELECT a.*, d.imei, d.model as device_model, p.name as plan_name
      FROM accounts a
      JOIN devices d ON a.device_id = d.id
      JOIN plans p ON a.plan_id = p.id
      WHERE a.id = ? AND a.dealer_id = ?
    `,
    args: [params.id, locals.dealer.id]
  });

  const row = result.rows[0];
  const nextDue = Number(row.next_payment_due);
  const amtPaid = Number(row.amount_paid);
  const totalLoan = Number(row.total_loan_amount);
  const status: Status = row.locked_by_dealer === 1
    ? 'LOCKED'
    : computeStatus(nextDue);

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
    status
  };

  return json(customer);
};