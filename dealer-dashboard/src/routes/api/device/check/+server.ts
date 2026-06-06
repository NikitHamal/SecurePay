import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { db } from '$lib/db';
import { computeStatus, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ url }) => {
  const imei = url.searchParams.get('imei');

  if (!imei) {
    return errorResponse('IMEI parameter is required', 400);
  }

  const deviceResult = await db.execute({
    sql: 'SELECT id, imei, model, status FROM devices WHERE imei = ?',
    args: [imei]
  });

  if (deviceResult.rows.length === 0) {
    return json({ enrolled: false, imei });
  }

  const device = deviceResult.rows[0];

  const accountResult = await db.execute({
    sql: 'SELECT * FROM accounts WHERE device_id = ?',
    args: [device.id as string]
  });

  if (accountResult.rows.length === 0) {
    return json({
      enrolled: false,
      imei: device.imei,
      deviceModel: device.model,
      deviceStatus: device.status
    });
  }

  const account = accountResult.rows[0];
  const status = account.locked_by_dealer === 1
    ? 'LOCKED'
    : computeStatus(Number(account.next_payment_due));

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
    remainingBalance: Math.max(0, Number(account.total_loan_amount) - Number(account.amount_paid))
  });
};