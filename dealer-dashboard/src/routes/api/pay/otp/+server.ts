import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, getDb, getPaystackSecret } from '$lib/api/server';
import { submitOtp, verifyTransaction } from '$lib/paystack';
import { applyPayment } from '$lib/payments';

export const POST: RequestHandler = async ({ request, platform }) => {
  const secret = getPaystackSecret({ platform });
  if (!secret) return errorResponse('Paystack is not configured', 503);

  const body = await request.json().catch(() => ({})) as Record<string, unknown>;
  const reference = String(body.reference ?? '').trim();
  const otp = String(body.otp ?? '').trim();

  if (!reference) return errorResponse('Reference is required', 400);
  if (!/^\d{4,8}$/.test(otp)) return errorResponse('OTP must be between 4 and 8 digits', 400);

  const db = getDb({ platform });

  const txRow = await db.prepare(`
    SELECT * FROM paystack_transactions WHERE reference = ?
  `).bind(reference).first<Record<string, any>>();

  if (!txRow) return errorResponse('Transaction not found', 404);

  try {
    const data = await submitOtp(reference, otp, secret);
    const nowSec = Math.floor(Date.now() / 1000);

    await db.prepare(`
      UPDATE paystack_transactions
         SET status = ?, gateway_response = ?, updated_at = ?
       WHERE reference = ?
    `).bind(data.status, (data.message || '').slice(0, 500), nowSec, reference).run();

    if (data.status === 'success' && !txRow.payment_id) {
      try {
        const verified = await verifyTransaction(reference, secret).catch(() => null);
        const amount = Number(verified?.amount || txRow.amount);
        const paidAt = verified?.paid_at ? Math.floor(new Date(verified.paid_at).getTime() / 1000) : nowSec;

        const { paymentId } = await applyPayment({
          db,
          accountId: txRow.account_id,
          amount,
          method: 'MOBILE_MONEY',
          reference: `paystack:${reference}`,
          recordedBy: 'web-portal',
          env: platform?.env
        });

        await db.prepare(`
          UPDATE paystack_transactions
             SET status = 'success',
                 gateway_response = 'Successful',
                 paid_at = ?,
                 payment_id = ?,
                 updated_at = ?
           WHERE reference = ?
        `).bind(paidAt, paymentId, nowSec, reference).run();
      } catch (applyErr) {
        console.error('[pay/otp] applyPayment error', applyErr);
      }

      return json({
        ok: true,
        status: 'success',
        reference,
        message: 'Payment completed successfully!'
      });
    }

    return json({
      ok: true,
      status: data.status,
      message: data.message || data.display_text || 'OTP submitted successfully',
      otp_required: data.status === 'send_otp' || data.status === 'otp_sent'
    });
  } catch (err: any) {
    const msg = err?.body?.data?.gateway_response || err?.body?.data?.message || err?.body?.message || err?.message || 'OTP submission failed';
    return json({ ok: false, message: msg }, { status: 400 });
  }
};
