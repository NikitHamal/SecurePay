import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ params, locals, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const token = params.token;
  if (!token || token.length < 16) {
    return errorResponse('Invalid token', 400);
  }

  const db = getDb({ platform });

  const row = await db.prepare(
    `SELECT t.id, t.activation_code, t.status, t.created_at, t.expires_at, t.provisioned_at, t.activated_at,
            a.id AS account_id, a.customer_name, d.imei, d.model
       FROM provisioning_tokens t
       JOIN accounts a ON t.account_id = a.id
       JOIN devices d ON t.device_id = d.id
      WHERE t.id = ? AND t.dealer_id = ?`
  ).bind(token, locals.dealer.id).first();

  if (!row) {
    return errorResponse('Provisioning token not found', 404);
  }

  return json({
    token: row.id,
    activationCode: row.activation_code,
    status: row.status,
    createdAt: Number(row.created_at) * 1000,
    expiresAt: Number(row.expires_at) * 1000,
    provisionedAt: row.provisioned_at ? Number(row.provisioned_at) * 1000 : null,
    activatedAt: row.activated_at ? Number(row.activated_at) * 1000 : null,
    account: {
      id: row.account_id,
      customerName: row.customer_name
    },
    device: {
      imei: row.imei,
      model: row.model
    }
  });
};
