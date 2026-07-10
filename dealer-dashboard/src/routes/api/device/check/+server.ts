import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse, releaseFields, releaseApproved, getDealerSecurityPolicy, generateDeviceApiSecret } from '$lib/api/server';

export const GET: RequestHandler = async ({ url, platform, locals }) => {
  if (!locals.hmacVerified) {
    return errorResponse('HMAC verification required', 401);
  }

  const imei = url.searchParams.get('imei');
  const accountId = String(url.searchParams.get('accountId') ?? '').trim();

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

  const account = accountId
    ? await db.prepare('SELECT * FROM accounts WHERE device_id = ? AND id = ?').bind(device.id as string, accountId).first()
    : await db.prepare('SELECT * FROM accounts WHERE device_id = ?').bind(device.id as string).first();

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

  // The app-level bootstrap HMAC is distributed inside the APK and therefore
  // must never be treated as authorization to disclose customer data or a
  // per-device credential. It is only enough to discover whether inventory is enrolled.
  if (locals.hmacScope !== 'device') {
    return json({
      enrolled: true,
      device: {
        id: device.id,
        imei: device.imei,
        model: device.model,
        status: device.status
      },
      apiSecret: '',
      serverTime: Date.now()
    }, { headers: { 'Cache-Control': 'no-store' } });
  }

  const nowSec = Math.floor(Date.now() / 1000);
  let apiSecret = String(account.device_hmac_secret ?? '').trim();
  if (apiSecret.length < 32) {
    apiSecret = generateDeviceApiSecret();
    await db.prepare(`
      UPDATE accounts
         SET device_hmac_secret = ?,
             device_hmac_secret_created_at = COALESCE(device_hmac_secret_created_at, ?),
             updated_at = ?
       WHERE id = ?
    `).bind(apiSecret, nowSec, nowSec, account.id as string).run();
  }

  const securityPolicy = await getDealerSecurityPolicy({ platform }, String(account.dealer_id));
  const release = releaseFields(account as Record<string, unknown>);
  const isStolen = Number(account.is_stolen ?? 0) === 1;
  const status = releaseApproved(account as Record<string, unknown>)
    ? 'ACTIVE'
    : (isStolen ? 'STOLEN' : (account.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(Number(account.next_payment_due))));

  return json({
    enrolled: true,
    device: {
      id: device.id,
      imei: device.imei,
      model: device.model,
      status: device.status
    },
    apiSecret,
    account: {
      id: account.id,
      customerName: account.customer_name,
      status,
      nextPaymentDue: Number(account.next_payment_due),
      amountPaid: Number(account.amount_paid),
      totalLoanAmount: Number(account.total_loan_amount),
      dailyRate: Number(account.daily_rate),
      isStolen,
      releaseApproved: release.releaseApproved,
      releaseApprovedAt: release.releaseApprovedAt,
      releasedAt: release.releasedAt
    },
    securityPolicy,
    serverTime: Date.now()
  });
};