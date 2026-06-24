import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse, releaseFields, releaseApproved, releaseHorizon } from '$lib/api/server';
import { parsePaymentMethod, paymentMethodStorageValue } from '$lib/payment-method';
import { v4 as uuidv4 } from 'uuid';
import type { Customer, Status } from '$lib/types';

const DAY_MS = 24 * 60 * 60 * 1000;

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const body = await request.json();
  const accountId = String(body.accountId ?? '').trim();
  const amount = Number(body.amount);
  const method = parsePaymentMethod(body.method);
  const reference = typeof body.reference === 'string' ? body.reference.trim().slice(0, 120) : '';

  if (!accountId || !Number.isSafeInteger(amount) || amount <= 0 || !method) {
    return errorResponse('A valid accountId, positive integer amount in pesewas, and payment method are required', 400);
  }

  const db = getDb({ platform });
  const account = await db.prepare('SELECT * FROM accounts WHERE id = ? AND dealer_id = ?')
    .bind(accountId, locals.dealer.id)
    .first();

  if (!account) {
    return errorResponse('Account not found', 404);
  }

  const currentPaid = Number(account.amount_paid);
  const totalLoan = Number(account.total_loan_amount);
  if (amount > Math.max(0, totalLoan - currentPaid)) {
    return errorResponse('Payment exceeds the remaining loan balance', 409);
  }

  const newAmountPaid = currentPaid + amount;
  const paidOff = newAmountPaid >= totalLoan;
  const dailyRate = Number(account.daily_rate);
  if (!Number.isSafeInteger(dailyRate) || dailyRate <= 0) {
    return errorResponse('Account daily rate is invalid', 409);
  }

  const currentDue = Number(account.next_payment_due);
  const now = Date.now();
  const nowSec = Math.floor(now / 1000);
  const base = Math.max(currentDue, now);
  // Precise millisecond extension: every cent pays for (DAY_MS / dailyRate) ms.
  // Sub-day precision guaranteed — 5 GHS on 20 GHS/day = 6 hours exactly.
  const msExtended = Math.floor((amount / dailyRate) * DAY_MS);
  const newDue = paidOff
    ? releaseHorizon(now)
    : base + msExtended;

  const paymentId = uuidv4();
  await db.batch([
    db.prepare(
      `INSERT INTO payments (id, account_id, amount, method, reference, recorded_by, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).bind(
      paymentId,
      accountId,
      amount,
      paymentMethodStorageValue(method),
      reference || null,
      locals.dealer.id,
      Math.floor(now / 1000)
    ),
    db.prepare(
      `UPDATE accounts
          SET amount_paid = ?,
              next_payment_due = ?,
              locked_by_dealer = 0,
              release_approved = CASE WHEN ? THEN 1 ELSE release_approved END,
              release_approved_at = CASE WHEN ? THEN COALESCE(release_approved_at, ?) ELSE release_approved_at END,
              updated_at = ?
        WHERE id = ?`
    ).bind(newAmountPaid, newDue, paidOff ? 1 : 0, paidOff ? 1 : 0, nowSec, nowSec, accountId)
  ]);

  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ?
  `).bind(accountId).first();

  if (!row) {
    return errorResponse('Payment recorded but the account could not be reloaded', 500);
  }

  const nextDue = Number(row.next_payment_due);
  const amtPaid = Number(row.amount_paid);
  const status: Status = releaseApproved(row as Record<string, unknown>) ? 'ACTIVE' : (row.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(nextDue));

  const customer: Customer = {
    id: row.id as string,
    customerName: row.customer_name as string,
    nationalId: row.national_id as string,
    phoneNumber: row.phone_number as string,
    imei: row.imei as string,
    deviceModel: row.device_model as string,
    planName: row.plan_name as string,
    totalLoanAmount: totalLoan,
    amountPaid: amtPaid,
    remainingBalance: Math.max(0, totalLoan - amtPaid),
    dailyRate,
    nextPaymentDueEpochMillis: nextDue,
    status,
    ...releaseFields(row as Record<string, unknown>)
  };

  return json({
    payment: { id: paymentId, accountId, amount, method, reference: reference || null },
    account: customer
  });
};
