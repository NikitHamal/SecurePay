import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import {
  getDb,
  computeStatus,
  errorResponse,
  releaseFields,
  releaseApproved,
  getDealerSecurityPolicy,
  generateDeviceApiSecret
} from '$lib/api/server';

/**
 * Activates a provisioned phone and returns its per-device credential.
 * The operation is idempotent for the same unexpired 256-bit provisioning token
 * so a phone can safely recover after an HTTP timeout or a stale backend deploy.
 */
export const POST: RequestHandler = async ({ request, platform, locals }) => {
  if (!locals.hmacVerified || locals.hmacScope !== 'global') {
    return errorResponse('Bootstrap HMAC verification required', 401);
  }

  const body = await request.json() as Record<string, unknown>;
  const activationCode = String(body.activationCode ?? '').trim();
  const provisioningToken = String(body.provisioningToken ?? '').trim();
  const imei = String(body.imei ?? '').trim();

  if (!/^\d{6}$/.test(activationCode)) {
    return errorResponse('A valid 6-digit activation code is required', 400);
  }
  if (!/^[0-9a-f]{64}$/i.test(provisioningToken)) {
    return errorResponse('A valid provisioning token is required', 400);
  }
  if (!/^\d{15}$/.test(imei)) {
    return errorResponse('The 15-digit IMEI from provisioning is required', 400);
  }

  const db = getDb({ platform });
  const token = await db.prepare(
    `SELECT id, account_id, device_id, dealer_id, status, expires_at
       FROM provisioning_tokens
      WHERE activation_code = ? AND id = ?`
  ).bind(activationCode, provisioningToken).first();
  if (!token) return errorResponse('Invalid activation credentials', 404);

  const nowSec = Math.floor(Date.now() / 1000);
  if (Number(token.expires_at) < nowSec) {
    await db.prepare("UPDATE provisioning_tokens SET status = 'expired' WHERE id = ? AND status != 'revoked'")
      .bind(String(token.id)).run();
    return errorResponse('This activation code has expired. Ask your dealer for a new one.', 410);
  }
  if (token.status === 'revoked' || token.status === 'expired') {
    return errorResponse('This activation code is no longer valid', 410);
  }
  if (!['pending', 'provisioned', 'activated'].includes(String(token.status))) {
    return errorResponse('This activation code cannot be used', 409);
  }

  const device = await db.prepare('SELECT id, imei, model FROM devices WHERE id = ?')
    .bind(String(token.device_id)).first();
  const account = await db.prepare('SELECT * FROM accounts WHERE id = ?')
    .bind(String(token.account_id)).first();
  if (!device || !account) return errorResponse('The linked device or account is missing', 500);
  if (String(device.imei) !== imei) {
    return errorResponse('IMEI does not match the provisioned inventory device', 409);
  }

  let deviceApiSecret = String(account.device_hmac_secret ?? '').trim();
  if (deviceApiSecret.length < 32) {
    deviceApiSecret = generateDeviceApiSecret();
    await db.prepare(`
      UPDATE accounts
         SET device_hmac_secret = ?,
             device_hmac_secret_created_at = COALESCE(device_hmac_secret_created_at, ?),
             updated_at = ?
       WHERE id = ?
    `).bind(deviceApiSecret, nowSec, nowSec, String(account.id)).run();
  }

  if (token.status !== 'activated') {
    await db.prepare(
      "UPDATE provisioning_tokens SET status = 'activated', activated_at = ? WHERE id = ? AND status IN ('pending', 'provisioned')"
    ).bind(nowSec, String(token.id)).run();
  }

  const release = releaseFields(account as Record<string, unknown>);
  const securityPolicy = await getDealerSecurityPolicy({ platform }, String(account.dealer_id));
  const isStolen = Number(account.is_stolen ?? 0) === 1;
  const status = releaseApproved(account as Record<string, unknown>)
    ? 'ACTIVE'
    : (isStolen ? 'STOLEN' : (account.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(Number(account.next_payment_due))));

  return json({
    enrolled: true,
    activated: true,
    recovered: token.status === 'activated',
    imei: device.imei,
    apiSecret: deviceApiSecret,
    device: { id: device.id, imei: device.imei, model: device.model },
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
  }, { headers: { 'Cache-Control': 'no-store' } });
};
