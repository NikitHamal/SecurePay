import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

const ALLOWED_LEVELS = new Set(['DEBUG', 'INFO', 'WARN', 'ERROR', 'PROVISIONING_ERROR']);
const MAX_TAG_LENGTH = 80;
const MAX_MESSAGE_LENGTH = 12_000;

/** Device-originated diagnostics. POST is authenticated by HMAC in hooks.server.ts. */
export const POST: RequestHandler = async ({ request, platform, locals }) => {
  if (!locals.hmacVerified) return errorResponse('HMAC verification required', 401);

  try {
    const body = await request.json() as Record<string, unknown>;
    const tag = String(body.tag ?? '').trim().slice(0, MAX_TAG_LENGTH);
    const message = String(body.message ?? '').trim().slice(0, MAX_MESSAGE_LENGTH);
    const requestedLevel = String(body.level ?? 'INFO').trim().toUpperCase();
    const level = ALLOWED_LEVELS.has(requestedLevel) ? requestedLevel : 'INFO';

    if (!tag || !message) return errorResponse('Invalid log format', 400);

    const db = getDb({ platform });
    const accountId = locals.deviceId ?? null;
    const imei = locals.deviceImei ?? null;
    let deviceModel: string | null = null;
    if (accountId) {
      const row = await db.prepare(
        "SELECT d.model FROM accounts a JOIN devices d ON d.id = a.device_id WHERE a.id = ?"
      ).bind(accountId).first<{ model: string }>();
      deviceModel = row?.model ?? null;
    }
    await db.prepare(
      'INSERT INTO device_logs (tag, message, level, account_id, imei, device_model) VALUES (?, ?, ?, ?, ?, ?)'
    ).bind(tag, message, level, accountId, imei, deviceModel).run();
    return json({ success: true }, { status: 201 });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Server error';
    return errorResponse(message, 500);
  }
};

/** Logs are global diagnostics, so only the global owner may read or clear them. */
export const GET: RequestHandler = async ({ platform, locals }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (locals.dealer.role !== 'SUPER_ADMIN') return errorResponse('Insufficient permissions', 403);

  try {
    const db = getDb({ platform });
    const { results } = await db.prepare(`
      SELECT l.id, l.tag, l.message, l.level,
             l.created_at * 1000 AS time,
             l.account_id, l.imei, l.device_model,
             a.customer_name
      FROM device_logs l
      LEFT JOIN accounts a ON a.id = l.account_id
      ORDER BY l.id DESC LIMIT 200
    `).all();
    return json(results, { headers: { 'Cache-Control': 'no-store' } });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Server error';
    return errorResponse(message, 500);
  }
};

export const DELETE: RequestHandler = async ({ platform, locals }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (locals.dealer.role !== 'SUPER_ADMIN') return errorResponse('Insufficient permissions', 403);

  try {
    const db = getDb({ platform });
    await db.prepare('DELETE FROM device_logs').run();
    return json({ success: true });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Server error';
    return errorResponse(message, 500);
  }
};
