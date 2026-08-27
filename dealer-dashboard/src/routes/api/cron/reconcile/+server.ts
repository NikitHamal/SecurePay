import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { requeryPendingPayments } from '$lib/paystack-sync';

/**
 * Paystack reconciliation sweep, triggerable by cron.
 *
 * Cloudflare Pages Functions cannot schedule cron jobs themselves, so a small
 * standalone cron Worker calls this endpoint on an interval with a shared
 * secret (CRON_SECRET) in the x-cron-secret header. Verified Paystack
 * settlements are applied and, when they reach the down-payment threshold,
 * auto-confirm the down payment so the customer's phone unlocks without an
 * admin needing to be online.
 */
export const POST: RequestHandler = async ({ request, platform }) => {
  const expected = platform?.env?.CRON_SECRET ?? '';
  const provided = request.headers.get('x-cron-secret') ?? '';
  if (!expected || provided.length !== expected.length || provided !== expected) {
    return errorResponse('Forbidden', 403);
  }

  const secret = platform?.env?.PAYSTACK_SECRET_KEY ?? '';
  if (!secret) {
    return errorResponse('PAYSTACK_SECRET_KEY is not configured', 500);
  }

  const db = getDb({ platform });
  let results;
  try {
    results = await requeryPendingPayments(db, secret, {});
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    return json({ ok: false, error: message }, { status: 500 });
  }

  const summary = {
    ok: true,
    swept: results.length,
    applied: results.filter((r) => r.applied).length,
    statuses: results.reduce<Record<string, number>>((acc, r) => {
      acc[r.paystackStatus] = (acc[r.paystackStatus] ?? 0) + 1;
      return acc;
    }, {})
  };

  return json(summary);
};