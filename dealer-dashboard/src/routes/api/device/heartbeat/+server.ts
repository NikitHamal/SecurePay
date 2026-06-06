import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse } from '$lib/api/server';

export const POST: RequestHandler = async ({ request, platform }) => {
  const body = await request.json();
  const { imei } = body;

  if (!imei) {
    return errorResponse('IMEI is required', 400);
  }

  const db = getDb({ platform });

  const device = await db.prepare('SELECT id, imei, model FROM devices WHERE imei = ?').bind(imei).first();

  if (!device) {
    return errorResponse('Device not found', 404);
  }

  const account = await db.prepare('SELECT * FROM accounts WHERE device_id = ?').bind(device.id as string).first();

  if (!account) {
    return json({
      enrolled: false,
      device: {
        id: device.id,
        imei: device.imei,
        model: device.model
      }
    });
  }

  const now = Date.now();
  await db.prepare('UPDATE accounts SET updated_at = ? WHERE id = ?').bind(Math.floor(now / 1000), account.id as string).run();

  const status = account.locked_by_dealer === 1
    ? 'LOCKED'
    : computeStatus(Number(account.next_payment_due));

  return json({
    enrolled: true,
    device: {
      id: device.id,
      imei: device.imei,
      model: device.model
    },
    account: {
      id: account.id,
      customerName: account.customer_name,
      status,
      nextPaymentDue: Number(account.next_payment_due),
      amountPaid: Number(account.amount_paid),
      totalLoanAmount: Number(account.total_loan_amount),
      dailyRate: Number(account.daily_rate)
    }
  });
};