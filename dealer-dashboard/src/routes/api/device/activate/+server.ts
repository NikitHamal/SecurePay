import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse, releaseFields, releaseApproved } from '$lib/api/server';

export const POST: RequestHandler = async ({ request, platform, locals }) => {
  if (!locals.hmacVerified) {
    return errorResponse('HMAC verification required', 401);
  }

  const body = await request.json();
  const { activationCode, provisioningToken } = body;

  if (!activationCode || !/^\d{6}$/.test(String(activationCode))) {
    return errorResponse('A valid 6-digit activation code is required', 400);
  }
  if (!provisioningToken || !/^[0-9a-f]{64}$/i.test(String(provisioningToken))) {
    return errorResponse('A valid provisioning token is required', 400);
  }

  const db = getDb({ platform });

  const token = await db.prepare(
    `SELECT id, account_id, device_id, status, expires_at
       FROM provisioning_tokens
      WHERE activation_code = ? AND id = ?`
  ).bind(String(activationCode), String(provisioningToken)).first();

  if (!token) {
    return errorResponse('Invalid activation credentials', 404);
  }

  const nowSec = Math.floor(Date.now() / 1000);

  if (token.status === 'activated') {
    return errorResponse('This activation code has already been used', 410);
  }

  if (token.status === 'revoked' || token.status === 'expired') {
    return errorResponse('This activation code is no longer valid', 410);
  }

  if (Number(token.expires_at) < nowSec) {
    await db.prepare("UPDATE provisioning_tokens SET status = 'expired' WHERE id = ?").bind(token.id as string).run();
    return errorResponse('This activation code has expired. Ask your dealer for a new one.', 410);
  }

  if (token.status !== 'pending' && token.status !== 'provisioned') {
    return errorResponse('This activation code cannot be used', 409);
  }

  await db.prepare(
    "UPDATE provisioning_tokens SET status = 'activated', activated_at = ? WHERE id = ?"
  ).bind(nowSec, token.id as string).run();

  const device = await db.prepare('SELECT id, imei, model FROM devices WHERE id = ?').bind(token.device_id as string).first();
  const account = await db.prepare('SELECT * FROM accounts WHERE id = ?').bind(token.account_id as string).first();

  if (!device || !account) {
    return errorResponse('Activation succeeded but the linked account is missing', 500);
  }

  const status = releaseApproved(account as Record<string, unknown>)
    ? 'ACTIVE'
    : (account.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(Number(account.next_payment_due)));

  return json({
    enrolled: true,
    activated: true,
    imei: device.imei,
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
      releaseApproved: releaseFields(account as Record<string, unknown>).releaseApproved,
      releaseApprovedAt: releaseFields(account as Record<string, unknown>).releaseApprovedAt,
      releasedAt: releaseFields(account as Record<string, unknown>).releasedAt
    },
    serverTime: Date.now()
  });
};
