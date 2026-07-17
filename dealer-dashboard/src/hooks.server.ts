import type { Handle } from '@sveltejs/kit';
import type { D1Database } from '@cloudflare/workers-types';
import { verifyToken } from '$lib/auth';
import { verifyHmacSignature, getHmacSecret } from '$lib/hmac';

const DEVICE_PATHS = ['/api/device/check', '/api/device/heartbeat', '/api/device/payments', '/api/device/account', '/api/device/activate', '/api/device/release-complete', '/api/device/app-update', '/api/device/fcm-token', '/api/device/location', '/api/device/provisioned', '/api/device/customer-login', '/api/device/paystack'];
const GLOBAL_DEVICE_PATHS = ['/api/device/check', '/api/device/activate', '/api/device/app-update', '/api/device/provisioned', '/api/device/customer-login'];
const GLOBAL_LOG_PATH = '/api/device/logs';

const LOGIN_LIMIT = 10;
const LOGIN_WINDOW = 3600;
const GENERAL_RATE_LIMIT = 100;
const GENERAL_RATE_WINDOW = 60;

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

    // Location uploads can be sent as either a single ping or an offline batch.
    // Keep accountId/imei top-level so hooks can resolve the per-device HMAC
    // secret before the route handler consumes the request body.
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

async function checkRateLimit(db: D1Database, key: string, limit: number, window: number): Promise<boolean> {
  const nowSec = Math.floor(Date.now() / 1000);
  await db.prepare('DELETE FROM rate_limits WHERE created_at < ?')
    .bind(nowSec - window).run();
  const count = await db.prepare(
    'SELECT COUNT(*) as count FROM rate_limits WHERE key = ? AND created_at >= ?'
  ).bind(key, nowSec - window).first<{ count: number }>();
  if (count && count.count >= limit) return false;
  await db.prepare('INSERT INTO rate_limits (key, created_at) VALUES (?, ?)')
    .bind(key, nowSec).run();
  return true;
}

export const handle: Handle = async ({ event, resolve }) => {
  event.locals.hmacVerified = false;
  event.locals.hmacScope = undefined;

  const requestUrl = new URL(event.request.url);
  const path = requestUrl.pathname;

  const db = event.platform?.env?.DB;

  if (db && (path === '/api/auth/login' || path === '/api/device/customer-login') && event.request.method === 'POST') {
    const ip = event.request.headers.get('cf-connecting-ip') || 'unknown';
    const prefix = path === '/api/device/customer-login' ? 'customer-login' : 'login';
    const allowed = await checkRateLimit(db, `${prefix}:${ip}`, LOGIN_LIMIT, LOGIN_WINDOW);
    if (!allowed) {
      return jsonError('Too many login attempts. Try again in 1 hour.', 429);
    }
  }

  if (db && path.startsWith('/api/') && path !== '/api/auth/login' && event.request.method !== 'GET') {
    const ip = event.request.headers.get('cf-connecting-ip') || 'unknown';
    const allowed = await checkRateLimit(db, `api:${ip}`, GENERAL_RATE_LIMIT, GENERAL_RATE_WINDOW);
    if (!allowed) {
      return jsonError('Rate limit exceeded. Slow down.', 429);
    }
  }

  const requiresDeviceHmac = DEVICE_PATHS.some((p) => path.startsWith(p)) ||
    (path === GLOBAL_LOG_PATH && event.request.method === 'POST');

  if (requiresDeviceHmac) {
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

    const deviceDb = event.platform?.env?.DB;
    if (!deviceDb) return jsonError('Database unavailable', 503);

    const method = event.request.method;
    const urlPath = requestUrl.pathname + requestUrl.search;
    const bodyIdentity = parseBodyForAccount(body);
    const accountId = String(requestUrl.searchParams.get('accountId') ?? bodyIdentity?.accountId ?? '').trim();
    const imei = String(requestUrl.searchParams.get('imei') ?? bodyIdentity?.imei ?? '').trim();

    const deviceSecret = await lookupDeviceSecret(deviceDb, accountId, imei);
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
      event.locals.deviceId = accountId || undefined;
      event.locals.deviceImei = imei || undefined;
    } else {
      const allowGlobal = GLOBAL_DEVICE_PATHS.some((p) => path.startsWith(p)) || path === GLOBAL_LOG_PATH;
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
    await deviceDb.prepare('DELETE FROM hmac_nonces WHERE created_at < ?')
      .bind(nowSec - 10 * 60)
      .run();
    const nonceResult = await deviceDb.prepare(
      'INSERT OR IGNORE INTO hmac_nonces (nonce, created_at) VALUES (?, ?)'
    ).bind(nonce, nowSec).run();
    if (!nonceResult.success || Number(nonceResult.meta.changes ?? 0) !== 1) {
      return jsonError('Replayed HMAC request', 409);
    }

    event.locals.hmacVerified = true;
  }

  const authHeader = event.request.headers.get('authorization');
  const token = authHeader?.replace(/^Bearer\s+/i, '') || event.cookies.get('tb_session') || undefined;

  if (token && event.platform?.env?.JWT_SECRET && path.startsWith('/api/')) {
    const tokenDealer = verifyToken(token, event.platform.env.JWT_SECRET);
    if (tokenDealer && db) {
      // Resolve authority from the current database row, not from stale JWT claims.
      // This makes account disablement and role/scope changes effective immediately.
      const currentDealer = await db.prepare(`
        SELECT id, name, role, agency_id, branch_id
        FROM dealers
        WHERE id = ? AND is_approved = 1
      `).bind(tokenDealer.sub).first<{
        id: string;
        name: string;
        role: string;
        agency_id?: string | null;
        branch_id?: string | null;
      }>();
      const allowedRoles = new Set(['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN', 'AGENT']);
      if (currentDealer && allowedRoles.has(currentDealer.role)) {
        event.locals.dealer = {
          id: currentDealer.id,
          name: currentDealer.name,
          role: currentDealer.role as 'SUPER_ADMIN' | 'AGENCY_OWNER' | 'BRANCH_ADMIN' | 'AGENT',
          agencyId: currentDealer.agency_id || null,
          branchId: currentDealer.branch_id || null
        };
      }
    }
  }

  return resolve(event);
};
