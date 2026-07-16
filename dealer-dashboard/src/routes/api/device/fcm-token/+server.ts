import { json } from '@sveltejs/kit';
import { getDb, errorResponse } from '$lib/api/server';
import type { RequestEvent } from '@sveltejs/kit';

export const POST = async ({ request, platform, locals }: RequestEvent) => {
  if (!locals.hmacVerified) {
    return errorResponse('HMAC verification required', 401);
  }

  const body = await request.json() as Record<string, unknown>;
  const { accountId, imei, fcmToken } = body;

  if (!accountId || !/^\d{15}$/.test(String(imei ?? ''))) {
    return errorResponse('A valid accountId and imei are required', 400);
  }

  if (!fcmToken || typeof fcmToken !== 'string' || fcmToken.length < 32) {
    return errorResponse('A valid FCM token is required', 400);
  }

  const db = getDb({ platform });
  const now = Math.floor(Date.now() / 1000);

  await db.prepare(
    'UPDATE accounts SET fcm_token = ?, fcm_token_updated_at = ?, updated_at = ? WHERE id = ?'
  ).bind(fcmToken, now, now, accountId).run();

  return json({ success: true });
};
