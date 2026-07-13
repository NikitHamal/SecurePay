import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

export const POST: RequestHandler = async ({ request, platform, locals }) => {
  if (!locals.hmacVerified) {
    return errorResponse('HMAC verification required', 401);
  }

  try {
    const body = await request.json();
    const accountId = String(body.accountId ?? '').trim();
    const imei = String(body.imei ?? '').trim();

    if (!accountId || !/^\d{15}$/.test(imei)) {
      return errorResponse('A valid accountId and 15-digit IMEI are required', 400);
    }

    const db = getDb({ platform });

    const account = await db.prepare(`
      SELECT a.id FROM accounts a
      JOIN devices d ON d.id = a.device_id
      WHERE a.id = ? AND d.imei = ?
    `).bind(accountId, imei).first<{ id: string }>();

    if (!account) {
      return errorResponse('Device account not found', 404);
    }

    const now = Math.floor(Date.now() / 1000);

    await db.prepare(`
      UPDATE accounts SET last_sync_at = ?, updated_at = ? WHERE id = ?
    `).bind(now, now, accountId).run();

    await db.prepare(`
      INSERT INTO sync_reports (id, account_id, imei, app_version, battery_level, lat, lng, reported_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      uuidv4(),
      accountId,
      imei,
      body.appVersion ?? null,
      body.batteryLevel != null ? Number(body.batteryLevel) : null,
      body.lat != null ? Number(body.lat) : null,
      body.lng != null ? Number(body.lng) : null,
      now
    ).run();

    return json({ success: true, serverTime: Date.now() });
  } catch (e) {
    return errorResponse('Invalid request body', 400);
  }
};
