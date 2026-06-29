import type { Handle } from '@sveltejs/kit';
import type { D1Database } from '@cloudflare/workers-types';
import { verifyToken } from '$lib/auth';
import { verifyHmacSignature, getHmacSecret } from '$lib/hmac';

const DEVICE_PATHS = ['/api/device/check', '/api/device/heartbeat', '/api/device/payments', '/api/device/account', '/api/device/activate', '/api/device/release-complete', '/api/device/app-update', '/api/device/fcm-token', '/api/device/location'];
const GLOBAL_DEVICE_PATHS = ['/api/device/check', '/api/device/activate', '/api/device/app-update'];

function jsonError(message: string, status: number): Response {
  return new Response(JSON.stringify({ error: message }), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}

function parseBodyForAccount(body: string): { accountId: string; imei: string } | null {
  if (!body) return null;
  try {
    const parsed = JSON.parse(body) as Record<string, unknown>;
    const accountId = String(parsed.accountId ?? '').trim();
    const imei = String(parsed.imei ?? '').trim();
    if (accountId && /^\d{15}$/.test(imei)) return { accountId, imei };
  } catch {
    // Ignore malformed body here; the route will return the user-facing error.
  }
  return null;
}

async function lookupDeviceSecret(db: D1Database, accountId: string, imei: string): Promise<string | null> {
  if (!accountId || !/^\d{15}$/.test(imei)) return null;
  const row = await db.prepare(`
    SELECT a.device_hmac_secret AS secret
      FROM accounts a
      JOIN devices d ON d.id = a.device_id
     WHERE a.id = ? AND d.imei = ?
  `).bind(accountId, imei).first<{ secret: string | null }>();
  const secret = String(row?.secret ?? '').trim();
  return secret.length >= 32 ? secret : null;
}

export const handle: Handle = async ({ event, resolve }) => {
  event.locals.hmacVerified = false;
  event.locals.hmacScope = undefined;

  const requestUrl = new URL(event.request.url);
  const path = requestUrl.pathname;

  if (DEVICE_PATHS.some((p) => path.startsWith(p))) {
    const signature = event.request.headers.get('x-signature');
    const timestamp = event.request.headers.get('x-timestamp');
    const nonce = event.request.headers.get('x-nonce');

    if (!signature || !timestamp || !nonce) {
      return jsonError('Missing HMAC headers', 401);
    }

    let body = '';
    if (event.request.method !== 'GET' && event.request.method !== 'HEAD') {
      body = await event.request.text();
      event.request = new Request(event.request, { body });
    }

    const db = event.platform?.env?.DB;
    if (!db) return jsonError('Database unavailable', 503);

    const method = event.request.method;
    const urlPath = requestUrl.pathname + requestUrl.search;
    const accountId = requestUrl.searchParams.get('accountId') ?? parseBodyForAccount(body)?.accountId ?? '';
    const imei = requestUrl.searchParams.get('imei') ?? parseBodyForAccount(body)?.imei ?? '';

    const deviceSecret = await lookupDeviceSecret(db, accountId, imei);
    let valid = false;

    if (deviceSecret) {
      valid = await verifyHmacSignature({
        signature,
        timestamp,
        nonce,
        method,
        path: urlPath,
        body,
        secret: deviceSecret
      });
      if (!valid) return jsonError('Invalid device HMAC signature', 401);
      event.locals.hmacScope = 'device';
    } else {
      const allowGlobal = GLOBAL_DEVICE_PATHS.some((p) => path.startsWith(p)) || Boolean(accountId && imei);
      if (!allowGlobal) return jsonError('Device credential required', 401);
      valid = await verifyHmacSignature({
        signature,
        timestamp,
        nonce,
        method,
        path: urlPath,
        body,
        secret: getHmacSecret(event)
      });
      if (!valid) return jsonError('Invalid HMAC signature', 401);
      event.locals.hmacScope = 'global';
    }

    const nowSec = Math.floor(Date.now() / 1000);
    // Keep only a short replay window and atomically reject a reused nonce.
    await db.prepare('DELETE FROM hmac_nonces WHERE created_at < ?')
      .bind(nowSec - 10 * 60)
      .run();
    const nonceResult = await db.prepare(
      'INSERT OR IGNORE INTO hmac_nonces (nonce, created_at) VALUES (?, ?)'
    ).bind(nonce, nowSec).run();
    if (!nonceResult.success || Number(nonceResult.meta.changes ?? 0) !== 1) {
      return jsonError('Replayed HMAC request', 409);
    }

    event.locals.hmacVerified = true;
  }

  const authHeader = event.request.headers.get('authorization');
  const token = authHeader?.replace('Bearer ', '');

  if (token && event.platform?.env?.JWT_SECRET) {
    const dealer = verifyToken(token, event.platform.env.JWT_SECRET);
    if (dealer) {
      event.locals.dealer = { id: dealer.sub, name: dealer.name };
    }
  }

  return resolve(event);
};
