import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { parsePaymentMethod } from '$lib/payment-method';
import type { LedgerEntry, PaymentMethod } from '$lib/types';
import { getAccountScopeFilter } from '$lib/auth';

export const GET: RequestHandler = async ({ locals, url, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const requestedMethod = url.searchParams.get('method');
  const methodFilter = requestedMethod ? parsePaymentMethod(requestedMethod) : null;
  if (requestedMethod && !methodFilter) {
    return errorResponse('Unsupported payment method', 400);
  }
  const accountIdFilter = url.searchParams.get('accountId');

  const db = getDb({ platform });
  const scope = getAccountScopeFilter(locals.dealer, 'a');

  let sql = `
    SELECT p.*, a.customer_name, d.imei
    FROM payments p
    JOIN accounts a ON p.account_id = a.id
    JOIN devices d ON a.device_id = d.id
    WHERE ${scope.where}
  `;
  const args: (string | number)[] = [...scope.params];

  if (accountIdFilter) {
    sql += ' AND p.account_id = ?';
    args.push(accountIdFilter);
  }

  sql += ' ORDER BY p.created_at DESC';

  const result = await db.prepare(sql).bind(...args).all();

  const entries: LedgerEntry[] = result.results
    .map((row) => {
      const method = parsePaymentMethod(row.method);
      if (!method) return null;
      return {
        id: row.id as string,
        customerId: row.account_id as string,
        customerName: row.customer_name as string,
        imei: row.imei as string,
        amount: Number(row.amount),
        dateEpochMillis: Number(row.created_at) * 1000,
        method,
        reference: (row.reference as string) || ''
      } satisfies LedgerEntry;
    })
    .filter((entry): entry is LedgerEntry => entry !== null)
    .filter((entry) => !methodFilter || entry.method === (methodFilter as PaymentMethod));

  return json(entries);
};
