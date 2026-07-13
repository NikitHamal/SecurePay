import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, generateCustomerPin, getDb } from '$lib/api/server';
import { getAccountScopeFilter, hashPassword } from '$lib/auth';

export const POST: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });
  const scope = getAccountScopeFilter(locals.dealer, 'a');
  const account = await db.prepare(`
    SELECT a.id, a.phone_number
    FROM accounts a
    WHERE a.id = ? AND ${scope.where}
  `).bind(params.id, ...scope.params).first<{ id: string; phone_number: string }>();
  if (!account) return errorResponse('Account not found', 404);

  const accountNumber = String(account.phone_number ?? '').replace(/\D/g, '');
  if (accountNumber.length < 8 || accountNumber.length > 15) {
    return errorResponse('Customer phone number cannot be used as an account number', 409);
  }

  const temporaryPin = generateCustomerPin();
  const pinHash = hashPassword(temporaryPin);
  const nowSec = Math.floor(Date.now() / 1000);
  await db.prepare(`
    UPDATE accounts
       SET customer_account_number = ?,
           customer_pin_hash = ?,
           customer_pin_updated_at = ?,
           updated_at = ?
     WHERE id = ?
  `).bind(accountNumber, pinHash, nowSec, nowSec, account.id).run();

  return json({
    accountNumber,
    temporaryPin,
    message: 'Give these credentials to the verified customer. The PIN is shown only in this response.'
  }, { headers: { 'Cache-Control': 'no-store' } });
};
