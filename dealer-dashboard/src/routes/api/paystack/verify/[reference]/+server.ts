import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, getDb, getPaystackSecret } from '$lib/api/server';
import { verifyTransaction } from '$lib/paystack';
import { applyPayment } from '$lib/payments';

/** GET /api/paystack/verify/:reference — verify a Paystack transaction and optionally apply it. */
export const GET: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  const secret = getPaystackSecret({ platform });
  if (!secret) return errorResponse('Paystack is not configured', 503);

  const reference = params.reference;
  const db = getDb({ platform });

  const row = await db.prepare(`
    SELECT pt.*, a.dealer_id
      FROM paystack_transactions pt
      JOIN accounts a ON a.id = pt.account_id
     WHERE pt.reference = ?
  `).bind(reference).first<Record<string, any>>();

  if (!row) return errorResponse('Transaction not found', 404);

  // Authorize: must belong to dealer's scope.
  if (locals.dealer.role !== 'SUPER_ADMIN' && row.dealer_id !== locals.dealer.id &&
      !(locals.dealer.agencyId || locals.dealer.branchId)) {
    return errorResponse('Unauthorized', 403);
  }

  try {
    const data = await verifyTransaction(reference, secret);

    // If success and not already linked to a payment, apply payment (idempotent).
    if (data.status === 'success' && !row.payment_id) {
      const { paymentId } = await applyPayment({
        db,
        accountId: row.account_id,
        amount: Number(data.amount || row.amount),
        method: 'MOBILE_MONEY',
        reference: `paystack:${reference}`,
        recordedBy: 'paystack'
      });

      const paidAt = data.paid_at ? Math.floor(new Date(data.paid_at).getTime() / 1000) : Math.floor(Date.now() / 1000);
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
        data.id || null,
        data.authorization?.authorization_code || null,
        data.channel || row.channel || null,
        data.fees != null ? Math.round(Number(data.fees)) : null,
        paidAt,
        paymentId,
        reference
      ).run();
    } else if (data.status && data.status !== row.status) {
      await db.prepare(`UPDATE paystack_transactions SET status = ?, updated_at = unixepoch() WHERE reference = ?`)
        .bind(data.status, reference).run();
    }

    return json({
      ok: true,
      reference,
      status: data.status,
      amount: data.amount,
      currency: data.currency,
      channel: data.channel,
      paidAt: data.paid_at || null,
      gatewayResponse: data.gateway_response || null,
      applied: data.status === 'success'
    });
  } catch (err: any) {
    return errorResponse(err.message || 'Verification failed', 502);
  }
};
