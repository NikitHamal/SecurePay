import type { Handle } from '@sveltejs/kit';
import { verifyToken } from '$lib/auth';
import { verifyHmacSignature, getHmacSecret } from '$lib/hmac';

const DEVICE_PATHS = ['/api/device/check', '/api/device/heartbeat', '/api/device/payments'];

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