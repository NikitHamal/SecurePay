import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, getDb, getPaystackSecret } from '$lib/api/server';
import { verifyTransaction } from '$lib/paystack';
import { applyPayment } from '$lib/payments';

export const GET: RequestHandler = async ({ params, platform }) => {
  const reference = String(params.reference || '').trim();
  if (!reference) return errorResponse('Reference required', 400);

  const secret = getPaystackSecret({ platform });
  const db = getDb({ platform });

  const existing = await db.prepare(`SELECT * FROM paystack_transactions WHERE reference = ?`).bind(reference).first<Record<string, any>>();
  if (!existing) {
    return errorResponse('Transaction not found', 404);
  }

  if (existing.status === 'success' && existing.payment_id) {
    return json({
      status: 'success',
      alreadyApplied: true,
      amount: existing.amount,
      reference: existing.reference
    });
  }

  let verified;
  try {
    verified = await verifyTransaction(reference, secret);
  } catch (err: any) {
    return json({
      status: existing.status || 'pending',
      gatewayResponse: 'Waiting for approval'
    });
  }

  if (verified.status === 'success') {
    const amount = Number(verified.amount || existing.amount);
    const paidAt = verified.paid_at ? Math.floor(new Date(verified.paid_at).getTime() / 1000) : Math.floor(Date.now() / 1000);
    const channel = String(verified.channel || existing.channel || 'mobile_money').toUpperCase();

    if (!existing.payment_id) {
      try {
        const { paymentId } = await applyPayment({
          db,
          accountId: existing.account_id,
          amount,
          method: channel === 'CARD' ? 'CARD' : 'MOBILE_MONEY',
          reference: `paystack:${reference}`,
          recordedBy: 'web-portal',
          env: platform?.env
        });

        await db.prepare(`
          UPDATE paystack_transactions
             SET id = COALESCE(?, id),
                 status = 'success',
                 gateway_response = 'Successful',
                 authorization_code = ?,
                 channel = ?,
                 fees = ?,
                 paid_at = ?,
                 payment_id = ?,
                 updated_at = unixepoch()
           WHERE reference = ?
        `).bind(
          verified.id || null,
          verified.authorization?.authorization_code || null,
          verified.channel || null,
          verified.fees != null ? Math.round(Number(verified.fees)) : null,
          paidAt,
          paymentId,
          reference
        ).run();
      } catch (err) {
        console.error('[pay/verify] applyPayment error', err);
      }
    }

    return json({
      status: 'success',
      amount,
      reference,
      gatewayResponse: verified.gateway_response || 'Successful'
    });
  }

  if (verified.status === 'failed') {
    await db.prepare(`UPDATE paystack_transactions SET status = 'failed', gateway_response = ?, updated_at = unixepoch() WHERE reference = ?`)
      .bind(verified.gateway_response || 'Failed', reference).run();

    return json({
      status: 'failed',
      gatewayResponse: verified.gateway_response || 'Payment was declined or timed out.'
    });
  }

  return json({
    status: verified.status || 'pending',
    gatewayResponse: verified.gateway_response || 'Pending customer PIN approval'
  });
};
