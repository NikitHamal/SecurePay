import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ url, platform, locals }) => {
  if (!locals.hmacVerified) {
    return errorResponse('HMAC verification required', 401);
  }

  const accountId = url.searchParams.get('accountId');

  if (!accountId) {
    return errorResponse('accountId parameter is required', 400);
  }

  const db = getDb({ platform });

  const account = await db.prepare('SELECT id FROM accounts WHERE id = ?').bind(accountId).first();

  if (!account) {
    return errorResponse('Account not found', 404);
  }

  const result = await db.prepare(
    'SELECT id, account_id, amount, method, reference, created_at FROM payments WHERE account_id = ? ORDER BY created_at DESC LIMIT 50'
  ).bind(accountId).run();

  const payments = (result.results as any[]).map((row) => ({
    id: row.id,
    accountId: row.account_id,
    amount: Number(row.amount),
    method: row.method,
    reference: row.reference,
    createdAt: Number(row.created_at) * 1000
  }));

  return json({ payments });
};