import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse, releaseApproved } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

export const POST: RequestHandler = async ({ request, platform, locals }) => {
  if (!locals.hmacVerified) return errorResponse('HMAC verification required', 401);

  const body = await request.json();
  const accountId = String(body.accountId ?? '').trim();
  const imei = String(body.imei ?? '').trim();

  if (!accountId || !/^\d{15}$/.test(imei)) {
    return errorResponse('A valid accountId and 15-digit IMEI are required', 400);
  }

  const db = getDb({ platform });
  const row = await db.prepare(`
    SELECT a.*, d.id AS device_id, d.imei
      FROM accounts a
      JOIN devices d ON d.id = a.device_id
     WHERE a.id = ? AND d.imei = ?
  `).bind(accountId, imei).first();

  if (!row) return errorResponse('Device account not found', 404);
  if (!releaseApproved(row as Record<string, unknown>)) {
    return errorResponse('Release has not been approved for this account', 409);
  }

  const nowSec = Math.floor(Date.now() / 1000);
  await db.batch([
    db.prepare('UPDATE accounts SET released_at = COALESCE(released_at, ?), updated_at = ? WHERE id = ?')
      .bind(nowSec, nowSec, accountId),
    db.prepare("UPDATE devices SET status = 'released' WHERE id = ?")
      .bind(row.device_id as string),
    db.prepare("INSERT INTO lock_events (id, account_id, event_type, triggered_by, created_at) VALUES (?, ?, 'released_on_device', 'device', ?)")
      .bind(uuidv4(), accountId, nowSec)
  ]);

  return json({ ok: true, releasedAt: nowSec * 1000, serverTime: Date.now() });
};
