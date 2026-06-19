import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse, releaseFields, releaseApproved, getDealerSecurityPolicy } from '$lib/api/server';

export const POST: RequestHandler = async ({ request, platform, locals }) => {
  if (!locals.hmacVerified) {
    return errorResponse('HMAC verification required', 401);
  }

  const body = await request.json();
  const imei = String(body.imei ?? '').trim();
  const accountId = String(body.accountId ?? '').trim();

  if (!/^\d{15}$/.test(imei) || !accountId) {
    return errorResponse('A valid IMEI and accountId are required', 400);
  }

  const db = getDb({ platform });

  const device = await db.prepare('SELECT id, imei, model FROM devices WHERE imei = ?').bind(imei).first();

  if (!device) {
    return errorResponse('Device not found', 404);
  }

  const account = await db.prepare('SELECT * FROM accounts WHERE device_id = ? AND id = ?').bind(device.id as string, accountId).first();

  if (!account) {
    return json({
      enrolled: false,
      device: {
        id: device.id,
        imei: device.imei,
        model: device.model
      },
      serverTime: Date.now()
    });
  }

  const now = Date.now();
  await db.prepare('UPDATE accounts SET updated_at = ? WHERE id = ?').bind(Math.floor(now / 1000), account.id as string).run();

  const securityPolicy = await getDealerSecurityPolicy({ platform }, String(account.dealer_id));
  const release = releaseFields(account as Record<string, unknown>);
  const status = releaseApproved(account as Record<string, unknown>)
    ? 'ACTIVE'
    : (account.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(Number(account.next_payment_due)));

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
      dailyRate: Number(account.daily_rate),
      releaseApproved: release.releaseApproved,
      releaseApprovedAt: release.releaseApprovedAt,
      releasedAt: release.releasedAt
    },
    securityPolicy,
    serverTime: Date.now()
  });
};