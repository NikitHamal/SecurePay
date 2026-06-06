import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { db } from '$lib/db';
import { computeStatus, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

export const POST: RequestHandler = async ({ request }) => {
  const { imei } = await request.json();

  if (!imei) {
    return errorResponse('IMEI is required', 400);
  }

  const deviceResult = await db.execute({
    sql: 'SELECT id, imei, model FROM devices WHERE imei = ?',
    args: [imei]
  });

  if (deviceResult.rows.length === 0) {
    return errorResponse('Device not found', 404);
  }

  const device = deviceResult.rows[0];

  const accountResult = await db.execute({
    sql: 'SELECT * FROM accounts WHERE device_id = ?',
    args: [device.id as string]
  });

  if (accountResult.rows.length === 0) {
    return errorResponse('No account for this device', 404);
  }

  const account = accountResult.rows[0];
  const now = Date.now();
  const status = account.locked_by_dealer === 1
    ? 'LOCKED'
    : computeStatus(Number(account.next_payment_due), now);

  await db.execute({
    sql: 'UPDATE accounts SET updated_at = ? WHERE id = ?',
    args: [Math.floor(now / 1000), account.id as string]
  });

  return json({
    enrolled: true,
    imei: device.imei,
    deviceModel: device.model,
    accountId: account.id as string,
    customerName: account.customer_name as string,
    status,
    nextPaymentDue: Number(account.next_payment_due),
    dailyRate: Number(account.daily_rate),
    amountPaid: Number(account.amount_paid),
    totalLoanAmount: Number(account.total_loan_amount),
    remainingBalance: Math.max(0, Number(account.total_loan_amount) - Number(account.amount_paid)),
    serverTime: now
  });
};