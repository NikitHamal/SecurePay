import type { RequestHandler } from './$types';
import { errorResponse, getDb, getPaystackSecret } from '$lib/api/server';
import { verifyTransaction, verifyWebhookSignature } from '$lib/paystack';
import { applyPayment } from '$lib/payments';

/**
 * Paystack webhook endpoint.
 *
 * Paystack signs POSTs with X-Paystack-Signature = HMAC-SHA256(rawBody, secretKey).
 * We verify the signature BEFORE parsing the body, then act on charge.success.
 *
 * This endpoint MUST be publicly reachable (no auth). Configure it at:
 * https://dashboard.paystack.com/#/settings/developer
 *   Webhook URL: https://<your-dashboard>/api/paystack/webhook
 *   Events to send: charge.success, charge.failed, charge.dispute
 */
export const POST: RequestHandler = async ({ request, platform }) => {
  const secret = getPaystackSecret({ platform });
  if (!secret) return errorResponse('Paystack is not configured', 503);

  const signature = request.headers.get('x-paystack-signature');
  const rawBody = await request.text();

  const okSig = await (await import('$lib/paystack')).verifyWebhookSignatureAsync(rawBody, signature, secret);
  if (!okSig) {
    return new Response(JSON.stringify({ error: 'Invalid signature' }), { status: 401, headers: { 'Content-Type': 'application/json' } });
  }

  let event: { event?: string; data?: any };
  try { event = JSON.parse(rawBody); } catch { return new Response('bad json', { status: 400 }); }

  if (event.event !== 'charge.success' || !event.data?.reference) {
    // Acknowledge receipt of all events; we only action success.
    return new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  }

  const reference = String(event.data.reference);
  const db = getDb({ platform });

  // Idempotency: load existing row.
  const existing = await db.prepare(`SELECT * FROM paystack_transactions WHERE reference = ?`).bind(reference).first<Record<string, any>>();
  if (!existing) {
    // Transaction wasn't initialized by us — don't auto-apply, but log.
    console.warn(`[paystack] received webhook for unknown reference ${reference}`);
    return new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  }
  if (existing.payment_id) {
    // Already applied — idempotent 200.
    return new Response(JSON.stringify({ ok: true, alreadyApplied: true }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  }

  // Re-verify server-to-server before applying the payment (don't trust the webhook body alone).
  let verified;
  try {
    verified = await verifyTransaction(reference, secret);
  } catch (err: any) {
    console.error('[paystack] verify failed during webhook', err);
    return new Response(JSON.stringify({ error: 'verify failed' }), { status: 502, headers: { 'Content-Type': 'application/json' } });
  }

  if (verified.status !== 'success') {
    await db.prepare(`UPDATE paystack_transactions SET status = ?, updated_at = unixepoch() WHERE reference = ?`)
      .bind(verified.status || 'failed', reference).run();
    return new Response(JSON.stringify({ ok: true, status: verified.status }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  }

  // Apply the payment to the account.
  const amount = Number(verified.amount || existing.amount);
  const paidAt = verified.paid_at ? Math.floor(new Date(verified.paid_at).getTime() / 1000) : Math.floor(Date.now() / 1000);
  const channel = String(verified.channel || existing.channel || 'mobile_money').toUpperCase();

  try {
    const { paymentId } = await applyPayment({
      db,
      accountId: existing.account_id,
      amount,
      method: channel === 'CARD' ? 'CARD' : 'MOBILE_MONEY',
      reference: `paystack:${reference}`,
      recordedBy: 'paystack'
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

    return new Response(JSON.stringify({ ok: true, paymentId }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  } catch (err: any) {
    console.error('[paystack] failed to apply payment', err);
    return new Response(JSON.stringify({ error: err.message || 'apply failed' }), { status: 500, headers: { 'Content-Type': 'application/json' } });
  }
};

/** Required so SvelteKit doesn't consume the body before we read raw text. */
export const config = {
  isr: false
};
