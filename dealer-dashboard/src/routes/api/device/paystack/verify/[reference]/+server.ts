import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, getDb, getPaystackSecret } from '$lib/api/server';
import { verifyTransaction } from '$lib/paystack';
import { applyPayment } from '$lib/payments';

/**
 * GET /api/device/paystack/verify/:reference
 *
 * Device/HMAC-authenticated polling endpoint used by the customer app to check
 * whether a charge completed. On success, idempotently applies the payment.
 */
export const GET: RequestHandler = async ({ locals, params, url, platform }) => {
  if (!locals.hmacVerified || locals.hmacScope !== 'device') {
    return errorResponse('Device HMAC verification required', 401);
  }

  const secret = getPaystackSecret({ platform });
  if (!secret) return errorResponse('Paystack is not configured', 503);

  const reference = params.reference;
  const accountId = String(url.searchParams.get('accountId') ?? locals.deviceId ?? '').trim();
  const imei = String(url.searchParams.get('imei') ?? locals.deviceImei ?? '').trim();
  if (!accountId || !/^\d{15}$/.test(imei)) return errorResponse('Device identity required', 400);

  const db = getDb({ platform });

  const row = await db.prepare(`
    SELECT pt.*
      FROM paystack_transactions pt
      JOIN accounts a ON a.id = pt.account_id
      JOIN devices d ON d.id = a.device_id
     WHERE pt.reference = ? AND a.id = ? AND d.imei = ?
  `).bind(reference, accountId, imei).first<Record<string, any>>();

  if (!row) return errorResponse('Transaction not found', 404);

  try {
    const data = await verifyTransaction(reference, secret);

    if (data.status === 'success' && !row.payment_id) {
      const { paymentId, newAmountPaid, newDue, paidOff } = await applyPayment({
        db,
        accountId,
        amount: Number(data.amount || row.amount),
        method: 'MOBILE_MONEY',
        reference: `paystack:${reference}`,
        recordedBy: 'customer-app'
      });

      const paidAt = data.paid_at
        ? Math.floor(new Date(data.paid_at).getTime() / 1000)
        : Math.floor(Date.now() / 1000);

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

      // Return fresh balance so the Android client can update immediately
      // without a separate /device/account round-trip.
      const fresh = await db.prepare(`
        SELECT a.id, a.amount_paid, a.total_loan_amount, a.daily_rate,
               a.next_payment_due, a.locked_by_dealer, a.status
          FROM accounts a WHERE a.id = ?
      `).bind(accountId).first<Record<string, any>>();

      return json({
        ok: true,
        reference,
        status: 'success',
        amount: data.amount,
        currency: data.currency,
        channel: data.channel,
        paidAt: data.paid_at || null,
        applied: true,
        paidOff: !!paidOff,
        newAmountPaid: Number(fresh?.amount_paid ?? newAmountPaid),
        nextPaymentDueEpochMillis: Number(fresh?.next_payment_due ?? newDue) * 1000,
        lockedByDealer: fresh ? Number(fresh.locked_by_dealer) === 1 : false
      });
    }

    if (data.status && data.status !== row.status) {
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
      applied: false
    });
  } catch (err: any) {
    return errorResponse(err.message || 'Verification failed', 502);
  }
};
