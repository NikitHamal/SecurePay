import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse, releaseFields, releaseApproved } from '$lib/api/server';
import { sendFcm } from '$lib/api/fcm';
import { v4 as uuidv4 } from 'uuid';
import type { Customer, Status } from '$lib/types';

export const POST: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const accountId = params.id;
  const db = getDb({ platform });

  const acct = await db.prepare('SELECT * FROM accounts WHERE id = ? AND dealer_id = ?').bind(accountId, locals.dealer.id).first();

  if (!acct) {
    return errorResponse('Account not found', 404);
  }
  if (releaseApproved(acct as Record<string, unknown>)) {
    return errorResponse('Cannot lock a release-approved account', 409);
  }

  const now = Date.now();
  const HOUR_MS = 60 * 60 * 1000;

  await db.prepare('UPDATE accounts SET locked_by_dealer = 1, next_payment_due = ?, updated_at = ? WHERE id = ?').bind(now - HOUR_MS, Math.floor(now / 1000), accountId).run();

  await db.prepare("INSERT INTO lock_events (id, account_id, event_type, triggered_by, created_at) VALUES (?, ?, 'lock', 'dealer', ?)").bind(uuidv4(), accountId, Math.floor(now / 1000)).run();

  const fcmToken = String(acct.fcm_token ?? '').trim();
  if (fcmToken) {
    const fcmEnv = platform?.env as { FCM_SERVICE_ACCOUNT_EMAIL?: string; FCM_SERVICE_ACCOUNT_PRIVATE_KEY?: string; FCM_PROJECT_ID?: string } | undefined;
    if (fcmEnv) {
      sendFcm(fcmToken, { type: 'lock', accountId, isStolen: 'false' }, fcmEnv).catch(() => {});
    }
  }

  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ?
  `).bind(accountId).first();

  const nextDue = Number(row!.next_payment_due);
  const amtPaid = Number(row!.amount_paid);
  const totalLoan = Number(row!.total_loan_amount);

  const customer: Customer = {
    id: row!.id as string,
    customerName: row!.customer_name as string,
    nationalId: row!.national_id as string,
    phoneNumber: row!.phone_number as string,
    imei: row!.imei as string,
    deviceModel: row!.device_model as string,
    planName: row!.plan_name as string || 'Custom',
    totalLoanAmount: totalLoan,
    amountPaid: amtPaid,
    remainingBalance: Math.max(0, totalLoan - amtPaid),
    dailyRate: Number(row!.daily_rate),
    nextPaymentDueEpochMillis: nextDue,
    status: Number(row!.is_stolen ?? 0) === 1 ? 'STOLEN' : 'LOCKED',
    lockedByDealer: Number(row!.locked_by_dealer ?? 0),
    isStolen: Number(row!.is_stolen ?? 0) === 1,
    termDays: Number(row!.term_days),
    downPayment: Number(row!.down_payment),
    ...releaseFields(row as Record<string, unknown>)
  };

  return json(customer);
};