import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { db } from '$lib/db';
import { computeStatus, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';
import type { Customer, Status } from '$lib/types';

export const GET: RequestHandler = async ({ locals, url }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const dealerId = locals.dealer.id;
  const statusFilter = url.searchParams.get('status') as Status | null;

  const result = await db.execute({
    sql: `
      SELECT a.*, d.imei, d.model as device_model, p.name as plan_name
      FROM accounts a
      JOIN devices d ON a.device_id = d.id
      JOIN plans p ON a.plan_id = p.id
      WHERE a.dealer_id = ?
      ORDER BY a.created_at DESC
    `,
    args: [dealerId]
  });

  const customers: Customer[] = result.rows.map((row) => {
    const nextPaymentDue = Number(row.next_payment_due);
    const amountPaid = Number(row.amount_paid);
    const totalLoanAmount = Number(row.total_loan_amount);
    const status: Status = row.locked_by_dealer === 1
      ? 'LOCKED'
      : computeStatus(nextPaymentDue);

    return {
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
  });

  if (statusFilter) {
    const filtered = customers.filter((c) => c.status === statusFilter);
    return json(filtered);
  }

  return json(customers);
};

export const POST: RequestHandler = async ({ locals, request }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const body = await request.json();
  const { customerName, nationalId, phoneNumber, imei, planId, downPayment } = body;

  if (!customerName || !nationalId || !phoneNumber || !imei || !planId) {
    return errorResponse('Missing required fields: customerName, nationalId, phoneNumber, imei, planId', 400);
  }

  const deviceResult = await db.execute({
    sql: 'SELECT id, imei, model, status FROM devices WHERE imei = ? AND dealer_id = ?',
    args: [imei, locals.dealer.id]
  });

  if (deviceResult.rows.length === 0) {
    return errorResponse('Device not found in your inventory', 404);
  }

  const device = deviceResult.rows[0];

  if (device.status === 'sold') {
    return errorResponse('Device is already sold', 409);
  }

  const planResult = await db.execute({
    sql: 'SELECT * FROM plans WHERE id = ?',
    args: [planId]
  });

  if (planResult.rows.length === 0) {
    return errorResponse('Plan not found', 404);
  }

  const plan = planResult.rows[0];
  const dp = Number(downPayment) || Number(plan.min_down_payment);
  const totalLoanAmount = Number(plan.total_amount);
  const dailyRate = Number(plan.daily_rate);
  const termDays = Number(plan.term_days);
  const now = Date.now();
  const nextPaymentDue = now + 24 * 60 * 60 * 1000;

  const accountId = `ACC-${100000 + Math.floor(Math.random() * 900000)}`;

  await db.execute({
    sql: `INSERT INTO accounts (id, customer_name, national_id, phone_number, device_id, dealer_id, plan_id, total_loan_amount, amount_paid, daily_rate, next_payment_due, status, locked_by_dealer, down_payment, term_days, currency_code, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    args: [
      accountId,
      customerName,
      nationalId,
      phoneNumber,
      device.id as string,
      locals.dealer.id,
      planId,
      totalLoanAmount,
      dp,
      dailyRate,
      nextPaymentDue,
      'ACTIVE',
      0,
      dp,
      termDays,
      'KES',
      Math.floor(now / 1000),
      Math.floor(now / 1000)
    ]
  });

  await db.execute({
    sql: "UPDATE devices SET status = 'sold' WHERE id = ?",
    args: [device.id as string]
  });

  if (dp > 0) {
    await db.execute({
      sql: `INSERT INTO payments (id, account_id, amount, method, reference, recorded_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)`,
      args: [uuidv4(), accountId, dp, 'cash', 'Down payment', locals.dealer.id, Math.floor(now / 1000)]
    });
  }

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
  const amtPaid = Number(row.amount_paid);
  const totalLoan = Number(row.total_loan_amount);
  const status: Status = computeStatus(Number(row.next_payment_due));

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
    nextPaymentDueEpochMillis: Number(row.next_payment_due),
    status
  };

  return json(customer, { status: 201 });
};