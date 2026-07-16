import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ url, platform, locals }) => {
  if (!locals.hmacVerified) return errorResponse('HMAC verification required', 401);

  const accountId = String(url.searchParams.get('accountId') ?? '').trim();
  const imei = String(url.searchParams.get('imei') ?? '').trim();
  if (!accountId || !/^\d{15}$/.test(imei)) {
    return errorResponse('A valid accountId and 15-digit IMEI are required', 400);
  }

  const db = getDb({ platform });
  const account = await db.prepare(`
    SELECT a.id
      FROM accounts a
      JOIN devices d ON d.id = a.device_id
     WHERE a.id = ? AND d.imei = ?
  `).bind(accountId, imei).first();

  if (!account) return errorResponse('Device account not found', 404);

  const result = await db.prepare(
    'SELECT id, account_id, amount, method, reference, created_at FROM payments WHERE account_id = ? ORDER BY created_at DESC LIMIT 50'
  ).bind(accountId).all();

  const payments = result.results.map((row) => ({
    id: row.id,
    accountId: row.account_id,
    amount: Number(row.amount),
    method: row.method,
    reference: row.reference,
    createdAt: Number(row.created_at) * 1000
  }));

  return json({ payments, serverTime: Date.now() });
};
