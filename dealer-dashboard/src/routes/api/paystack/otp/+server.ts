import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, getDb, getPaystackSecret } from '$lib/api/server';
import { getAccountScopeFilter } from '$lib/auth';
import { submitOtp, verifyTransaction } from '$lib/paystack';

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const secret = getPaystackSecret({ platform });
  if (!secret) return errorResponse('Paystack is not configured', 503);

  const body = await request.json().catch(() => ({})) as Record<string, unknown>;
  const reference = String(body.reference ?? '').trim();
  const otp = String(body.otp ?? '').trim();

  if (!reference) return errorResponse('Reference is required', 400);
  if (!/^\d{4,8}$/.test(otp)) return errorResponse('OTP must be 4-8 digits', 400);

  const db = getDb({ platform });
  const scope = getAccountScopeFilter(locals.dealer, 'a');

  const txRow = await db.prepare(`
    SELECT pt.*, a.dealer_id
      FROM paystack_transactions pt
      JOIN accounts a ON a.id = pt.account_id
     WHERE pt.reference = ? AND ${scope.where}
  `).bind(reference, ...scope.params).first<Record<string, any>>();

  if (!txRow) return errorResponse('Transaction not found', 404);

  try {
    const data = await submitOtp(reference, otp, secret);
    await db.prepare(`UPDATE paystack_transactions SET status = ?, gateway_response = ?, updated_at = ? WHERE reference = ?`)
      .bind(data.status, (data.message || '').slice(0, 500), Math.floor(Date.now() / 1000), reference).run();

    // If after submitting OTP Paystack says success, finalize via verify.
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
