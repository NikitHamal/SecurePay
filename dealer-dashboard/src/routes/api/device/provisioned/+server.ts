import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

/**
 * One-time provisioning milestone endpoint.
 *
 * The phone does not have a registered device credential yet, so this endpoint
 * authenticates with the 256-bit token embedded in the QR admin-extras bundle.
 */
export const POST: RequestHandler = async ({ request, platform, locals }) => {
  if (!locals.hmacVerified || locals.hmacScope !== 'global') {
    return errorResponse('Bootstrap HMAC verification required', 401);
  }

  const body = await request.json();
  const token = String(body.token ?? '').trim();
  const imei = String(body.imei ?? '').trim();

  if (!/^[0-9a-f]{64}$/i.test(token)) {
    return errorResponse('Invalid provisioning token', 400);
  }
  if (imei && !/^\d{15}$/.test(imei)) {
    return errorResponse('Invalid IMEI', 400);
  }

  const db = getDb({ platform });
  const row = await db.prepare(
    `SELECT t.id, t.status, t.expires_at, d.imei
       FROM provisioning_tokens t
       JOIN devices d ON d.id = t.device_id
      WHERE t.id = ?`
  ).bind(token).first();

  if (!row) {
    return errorResponse('Provisioning token not found', 404);
  }

  const nowSec = Math.floor(Date.now() / 1000);
  if (Number(row.expires_at) < nowSec) {
    await db.prepare("UPDATE provisioning_tokens SET status = 'expired' WHERE id = ?")
      .bind(token)
      .run();
    return errorResponse('Provisioning token expired', 410);
  }
  if (row.status === 'revoked' || row.status === 'expired') {
    return errorResponse('Provisioning token is no longer valid', 410);
  }
  if (imei && String(row.imei) !== imei) {
    return errorResponse('IMEI does not match the provisioned inventory device', 409);
  }

  if (row.status === 'pending') {
    await db.prepare(
      "UPDATE provisioning_tokens SET status = 'provisioned', provisioned_at = ? WHERE id = ? AND status = 'pending'"
    ).bind(nowSec, token).run();
  }

  return json({ ok: true, status: row.status === 'pending' ? 'provisioned' : row.status });
};
