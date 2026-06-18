import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';
import type { Customer, Status } from '$lib/types';

export const GET: RequestHandler = async ({ locals, url, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const dealerId = locals.dealer.id;
  const statusFilter = url.searchParams.get('status') as Status | null;

  const db = getDb({ platform });
  const result = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, p.name as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    JOIN plans p ON a.plan_id = p.id
    WHERE a.dealer_id = ?
    ORDER BY a.created_at DESC
  `).bind(dealerId).all();

  const customers: Customer[] = result.results.map((row) => {
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

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const body = await request.json();
  const { customerName, nationalId, phoneNumber, imei, planId, downPayment } = body;

  if (!customerName || !nationalId || !phoneNumber || !imei || !planId) {
    return errorResponse('Missing required fields: customerName, nationalId, phoneNumber, imei, planId', 400);
  }

  const db = getDb({ platform });

  const device = await db.prepare('SELECT id, imei, model, status FROM devices WHERE imei = ? AND dealer_id = ?').bind(imei, locals.dealer.id).first();

  if (!device) {
    return errorResponse('Device not found in your inventory', 404);
  }

  if (device.status === 'sold') {
    return errorResponse('Device is already sold', 409);
  }

  const plan = await db.prepare('SELECT * FROM plans WHERE id = ?').bind(planId).first();

  if (!plan) {
    return errorResponse('Plan not found', 404);
  }

  const dp = Number(downPayment) || Number(plan.min_down_payment);
  const totalLoanAmount = Number(plan.total_amount);
  const dailyRate = Number(plan.daily_rate);
  const termDays = Number(plan.term_days);
  const now = Date.now();
  const nextPaymentDue = now + 24 * 60 * 60 * 1000;

  const accountId = `ACC-${100000 + Math.floor(Math.random() * 900000)}`;

  await db.prepare(
    `INSERT INTO accounts (id, customer_name, national_id, phone_number, device_id, dealer_id, plan_id, total_loan_amount, amount_paid, daily_rate, next_payment_due, status, locked_by_dealer, down_payment, term_days, currency_code, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    accountId, customerName, nationalId, phoneNumber, device.id as string,
    locals.dealer.id, planId, totalLoanAmount, dp, dailyRate, nextPaymentDue,
    'ACTIVE', 0, dp, termDays, 'GHS', Math.floor(now / 1000), Math.floor(now / 1000)
  ).run();

  await db.prepare("UPDATE devices SET status = 'sold' WHERE id = ?").bind(device.id as string).run();

  if (dp > 0) {
    await db.prepare(
      `INSERT INTO payments (id, account_id, amount, method, reference, recorded_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).bind(uuidv4(), accountId, dp, 'cash', 'Down payment', locals.dealer.id, Math.floor(now / 1000)).run();
  }

  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, p.name as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ?
  `).bind(accountId).first();

  const amtPaid = Number(row!.amount_paid);
  const totalLoan = Number(row!.total_loan_amount);
  const status: Status = computeStatus(Number(row!.next_payment_due));

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
    nextPaymentDueEpochMillis: Number(row!.next_payment_due),
    status
  };

  return json(customer, { status: 201 });
};