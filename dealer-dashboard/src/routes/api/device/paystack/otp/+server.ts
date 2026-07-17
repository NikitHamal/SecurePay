import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, getDb, getPaystackSecret } from '$lib/api/server';
import { submitOtp, verifyTransaction } from '$lib/paystack';

/**
 * POST /api/device/paystack/otp — submit the OTP the customer received on their phone.
 * Device/HMAC-authenticated; scope enforced by accountId+imei.
 */
export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.hmacVerified || locals.hmacScope !== 'device') {
    return errorResponse('Device HMAC verification required', 401);
  }

  const secret = getPaystackSecret({ platform });
  if (!secret) return errorResponse('Paystack is not configured', 503);

  const body = await request.json().catch(() => ({})) as Record<string, unknown>;
  const reference = String(body.reference ?? '').trim();
  const otp = String(body.otp ?? '').trim();
  const accountId = String(body.accountId ?? locals.deviceId ?? '').trim();
  const imei = String(body.imei ?? locals.deviceImei ?? '').trim();

  if (!reference) return errorResponse('Reference is required', 400);
  if (!/^\d{4,8}$/.test(otp)) return errorResponse('OTP must be 4-8 digits', 400);
  if (!accountId || !/^\d{15}$/.test(imei)) return errorResponse('Device identity required', 400);

  const db = getDb({ platform });

  const txRow = await db.prepare(`
    SELECT pt.reference, pt.account_id
      FROM paystack_transactions pt
      JOIN accounts a ON a.id = pt.account_id
      JOIN devices d ON d.id = a.device_id
     WHERE pt.reference = ? AND a.id = ? AND d.imei = ?
  `).bind(reference, accountId, imei).first<Record<string, any>>();

  if (!txRow) return errorResponse('Transaction not found', 404);

  try {
    const data = await submitOtp(reference, otp, secret);
    await db.prepare(`UPDATE paystack_transactions SET status = ?, gateway_response = ?, updated_at = ? WHERE reference = ?`)
      .bind(data.status, (data.message || '').slice(0, 500), Math.floor(Date.now() / 1000), reference).run();

    if (data.status === 'success') {
      const verified = await verifyTransaction(reference, secret).catch(() => null);
      if (verified && verified.status === 'success') {
        return json({ ok: true, status: 'success', reference });
      }
    }

    return json({
      ok: true,
      status: data.status,
      message: data.message || data.display_text || 'OTP submitted',
      otpRequired: data.status === 'send_otp' || data.status === 'otp_sent'
    });
  } catch (err: any) {
    await db.prepare(`UPDATE paystack_transactions SET status = 'failed', gateway_response = ?, updated_at = ? WHERE reference = ?`)
      .bind((err.message || '').slice(0, 500), Math.floor(Date.now() / 1000), reference).run();
    return errorResponse(err.message || 'OTP submission failed', 502);
  }
};
