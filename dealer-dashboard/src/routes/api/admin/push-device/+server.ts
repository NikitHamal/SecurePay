import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { sendFcm, type FcmDataMessage } from '$lib/api/fcm';
import { getAccountScopeFilter } from '$lib/auth';

const VALID_TYPES = ['lock', 'unlock', 'sync', 'stolen', 'notification'] as const;

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const body = await request.json().catch(() => null);
  if (!body) return errorResponse('Invalid request body', 400);

  const { accountId, type, title, message } = body as Record<string, unknown>;
  if (!accountId || typeof accountId !== 'string') {
    return errorResponse('accountId is required', 400);
  }
  if (!type || !VALID_TYPES.includes(type as typeof VALID_TYPES[number])) {
    return errorResponse(`Invalid push type; must be one of: ${VALID_TYPES.join(', ')}`, 400);
  }

  const db = getDb({ platform });
  const scope = getAccountScopeFilter(locals.dealer, 'a');

  const acct = await db.prepare(
    `SELECT a.fcm_token, a.is_stolen FROM accounts a WHERE a.id = ? AND ${scope.where}`
  ).bind(accountId, ...scope.params).first<{ fcm_token: string | null; is_stolen: number }>();

  if (!acct) {
    return errorResponse('Account not found', 404);
  }

  const fcmToken = String(acct.fcm_token ?? '').trim();
  if (!fcmToken) {
    return errorResponse('Device has no FCM token registered', 400);
  }

  const fcmEnv = platform?.env as Record<string, string | undefined> | undefined;
  if (!fcmEnv) {
    return errorResponse('FCM environment not configured', 503);
  }

  const data: FcmDataMessage = { type: type as FcmDataMessage['type'], accountId };
  if (title) data.title = String(title);
  if (message) data.body = String(message);
  if (type === 'stolen' || (type === 'lock' && acct.is_stolen)) {
    data.isStolen = 'true';
  }

  try {
    await sendFcm(fcmToken, data, fcmEnv);
    return json({ success: true, accountId, type });
  } catch (e) {
    return errorResponse(`FCM send failed: ${e instanceof Error ? e.message : 'unknown error'}`, 503);
  }
};
