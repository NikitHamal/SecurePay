import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse, releaseFields, releaseApproved, getDealerSecurityPolicy } from '$lib/api/server';

/**
 * Device-scoped account endpoint.
 *
 * Customer devices must never call the dealer-only /api/accounts/:id route. This
 * endpoint is HMAC-protected by hooks.server.ts and additionally binds accountId
 * to the enrolled IMEI supplied by the managed device.
 */
export const GET: RequestHandler = async ({ url, platform, locals }) => {
  if (!locals.hmacVerified) return errorResponse('HMAC verification required', 401);

  const accountId = String(url.searchParams.get('accountId') ?? '').trim();
  const imei = String(url.searchParams.get('imei') ?? '').trim();
  if (!accountId || !/^\d{15}$/.test(imei)) {
    return errorResponse('A valid accountId and 15-digit IMEI are required', 400);
  }

  const db = getDb({ platform });
  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model AS device_model, COALESCE(p.name, 'Custom') AS plan_name
      FROM accounts a
      JOIN devices d ON d.id = a.device_id
      LEFT JOIN plans p ON p.id = a.plan_id
     WHERE a.id = ? AND d.imei = ?
  `).bind(accountId, imei).first();

  if (!row) return errorResponse('Device account not found', 404);

  const nextPaymentDue = Number(row.next_payment_due);
  const amountPaid = Number(row.amount_paid);
  const securityPolicy = await getDealerSecurityPolicy({ platform }, String(row.dealer_id));
  const totalLoanAmount = Number(row.total_loan_amount);
  const lockedByDealer = Number(row.locked_by_dealer) === 1;

  return json({
    id: row.id,
    customerName: row.customer_name,
    nationalId: row.national_id,
    phoneNumber: row.phone_number,
    imei: row.imei,
    deviceModel: row.device_model,
    planName: row.plan_name,
    totalLoanAmount,
    amountPaid,
    remainingBalance: Math.max(0, totalLoanAmount - amountPaid),
    dailyRate: Number(row.daily_rate),
    nextPaymentDueEpochMillis: nextPaymentDue,
    status: releaseApproved(row as Record<string, unknown>) ? 'ACTIVE' : (lockedByDealer ? 'LOCKED' : computeStatus(nextPaymentDue)),
    lockedByDealer: lockedByDealer ? 1 : 0,
    downPayment: Number(row.down_payment),
    termDays: Number(row.term_days),
    currencyCode: String(row.currency_code || 'GHS'),
    createdAt: Number(row.created_at) * 1000,
    updatedAt: Number(row.updated_at) * 1000,
    ...releaseFields(row as Record<string, unknown>),
    securityPolicy,
    serverTime: Date.now()
  });
};
