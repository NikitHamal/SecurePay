import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { db } from '$lib/db';
import { errorResponse } from '$lib/api/server';
import type { LedgerEntry, PaymentMethod } from '$lib/types';

export const GET: RequestHandler = async ({ locals, url }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const methodFilter = url.searchParams.get('method') as PaymentMethod | null;
  const accountIdFilter = url.searchParams.get('accountId');

  let sql = `
    SELECT p.*, a.customer_name, d.imei
    FROM payments p
    JOIN accounts a ON p.account_id = a.id
    JOIN devices d ON a.device_id = d.id
    WHERE a.dealer_id = ?
  `;
  const args: (string | number)[] = [locals.dealer.id];

  if (methodFilter) {
    sql += ' AND p.method = ?';
    args.push(methodFilter);
  }
  if (accountIdFilter) {
    sql += ' AND p.account_id = ?';
    args.push(accountIdFilter);
  }

  sql += ' ORDER BY p.created_at DESC';

  const result = await db.execute({ sql, args });

  const entries: LedgerEntry[] = result.rows.map((row) => ({
    id: row.id as string,
    customerId: row.account_id as string,
    customerName: row.customer_name as string,
    imei: row.imei as string,
    amount: Number(row.amount),
    dateEpochMillis: Number(row.created_at) * 1000,
    method: row.method as PaymentMethod,
    reference: (row.reference as string) || ''
  }));

  return json(entries);
};