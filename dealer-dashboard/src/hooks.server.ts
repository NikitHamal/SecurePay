import type { Handle } from '@sveltejs/kit';
import { verifyToken } from '$lib/auth';
import { verifyHmacSignature, getHmacSecret } from '$lib/hmac';

const DEVICE_PATHS = ['/api/device/check', '/api/device/heartbeat', '/api/device/payments', '/api/device/account', '/api/device/activate', '/api/device/release-complete', '/api/device/app-update'];

export const handle: Handle = async ({ event, resolve }) => {
  event.locals.hmacVerified = false;

  const path = new URL(event.request.url).pathname;

  if (DEVICE_PATHS.some((p) => path.startsWith(p))) {
    const signature = event.request.headers.get('x-signature');
    const timestamp = event.request.headers.get('x-timestamp');
    const nonce = event.request.headers.get('x-nonce');

    if (!signature || !timestamp || !nonce) {
      return new Response(JSON.stringify({ error: 'Missing HMAC headers' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    let body = '';
    if (event.request.method !== 'GET' && event.request.method !== 'HEAD') {
      body = await event.request.text();
      event.request = new Request(event.request, { body });
    }

    const hmacSecret = getHmacSecret(event);
    const method = event.request.method;
    const urlPath = new URL(event.request.url).pathname + new URL(event.request.url).search;

    const valid = await verifyHmacSignature({
      signature,
      timestamp,
      nonce,
      method,
      path: urlPath,
      body,
      secret: hmacSecret
    });

    if (!valid) {
      return new Response(JSON.stringify({ error: 'Invalid HMAC signature' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    const db = event.platform?.env?.DB;
    if (!db) {
      return new Response(JSON.stringify({ error: 'Database unavailable' }), {
        status: 503,
        headers: { 'Content-Type': 'application/json' }
      });
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
      return new Response(JSON.stringify({ error: 'Replayed HMAC request' }), {
        status: 409,
        headers: { 'Content-Type': 'application/json' }
      });
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