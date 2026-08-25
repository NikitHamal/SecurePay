import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, getDb, getPaystackSecret } from '$lib/api/server';
import { requeryPendingPayments } from '$lib/paystack-sync';

/**
 * POST/GET /api/paystack/sync
 *
 * Requeries Paystack for unapplied MoMo/card charges (last 48h) and credits
 * accounts idempotently. This is the manual fallback when the webhook did not
 * fire — e.g. a customer approved the MTN prompt after the USSD session closed.
 *
 * Admins sweep everything in scope; agents sweep only customers they enrolled.
 * Optional ?reference= forces a single-transaction check.
 */
async function handle(locals: App.Locals, platform: App.Platform | null | undefined, url: URL) {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  const secret = getPaystackSecret({ platform });
  if (!secret) return errorResponse('Paystack is not configured', 503);

  const reference = url.searchParams.get('reference')?.trim() || undefined;

  try {
    const db = getDb({ platform });
    const results = await requeryPendingPayments(db, secret, {
      reference,
      enrolledBy: locals.dealer.role === 'AGENT' ? locals.dealer.id : undefined
    });
    const applied = results.filter((r) => r.applied);
    return json({
      ok: true,
      checked: results.length,
      applied: applied.length,
      appliedReferences: applied.map((r) => r.reference),
      results
    });
  } catch (err: any) {
    console.error('[paystack-sync] failed', err);
    return errorResponse(err?.message || 'Sync failed', 502);
  }
}

export const GET: RequestHandler = async ({ locals, platform, url }) => handle(locals, platform, url);
export const POST: RequestHandler = async ({ locals, platform, url }) => handle(locals, platform, url);
