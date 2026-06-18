import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ url, platform, locals }) => {
  if (!locals.hmacVerified) {
    return errorResponse('HMAC verification required', 401);
  }

  const imei = url.searchParams.get('imei');

  if (!imei) {
    return errorResponse('IMEI parameter is required', 400);
  }

  if (!/^\d{15}$/.test(imei)) {
    return errorResponse('IMEI must be exactly 15 digits', 400);
  }

  const db = getDb({ platform });

  const device = await db.prepare('SELECT id, imei, model, status FROM devices WHERE imei = ?').bind(imei).first();

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
        model: device.model,
        status: device.status
      },
      serverTime: Date.now()
    });
  }

  const status = account.locked_by_dealer === 1
    ? 'LOCKED'
    : computeStatus(Number(account.next_payment_due));

  return json({
    enrolled: true,
    device: {
      id: device.id,
      imei: device.imei,
      model: device.model,
      status: device.status
    },
    account: {
      id: account.id,
      customerName: account.customer_name,
      status,
      nextPaymentDue: Number(account.next_payment_due),
      amountPaid: Number(account.amount_paid),
      totalLoanAmount: Number(account.total_loan_amount),
      dailyRate: Number(account.daily_rate)
    },
    serverTime: Date.now()
  });
};