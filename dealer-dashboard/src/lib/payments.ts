/**
 * Shared payment application logic used by both manual payments recorded in
 * the dashboard and Paystack webhook-driven payments.
 *
 * All monetary amounts are in pesewas (GHS subunit).
 */
import type { D1Database } from '@cloudflare/workers-types';
import { v4 as uuidv4 } from 'uuid';

const DAY_MS = 24 * 60 * 60 * 1000;

const RELEASE_HORIZON_SEC = 10 * 365 * 24 * 60 * 60;

export interface ApplyPaymentInput {
  db: D1Database;
  accountId: string;
  amount: number;          // pesewas
  method: string;          // e.g. 'MOBILE_MONEY', 'CARD', 'CASH', 'BANK'
  reference?: string;
  recordedBy: string;      // dealer id or 'paystack'
}

export interface ApplyPaymentResult {
  paymentId: string;
  newAmountPaid: number;
  newDue: number;          // epoch millis
  paidOff: boolean;
}

/**
 * Apply a payment to an account: insert payments row, update amount_paid,
 * advance next_payment_due, unlock if locked, auto-release if paid off.
 *
 * Does NOT check account scoping — caller must authorize before calling.
 */
export async function applyPayment(input: ApplyPaymentInput): Promise<ApplyPaymentResult> {
  const { db, accountId, amount, method, reference, recordedBy } = input;

  const account = await db.prepare(
    `SELECT * FROM accounts WHERE id = ?`
  ).bind(accountId).first<Record<string, unknown>>();
  if (!account) throw new Error('Account not found');

  const currentPaid = Number(account.amount_paid);
  const totalLoan = Number(account.total_loan_amount);
  const remaining = Math.max(0, totalLoan - currentPaid);
  if (amount > remaining + 1) throw new Error('Payment exceeds remaining loan balance');
  if (!Number.isFinite(amount) || amount <= 0) throw new Error('Amount must be positive');

  const dailyRate = Number(account.daily_rate);
  if (!Number.isFinite(dailyRate) || dailyRate <= 0) throw new Error('Account daily rate is invalid');

  const newAmountPaid = currentPaid + amount;
  const paidOff = newAmountPaid >= totalLoan;
  const currentDue = Number(account.next_payment_due);
  const now = Date.now();
  const nowSec = Math.floor(now / 1000);
  const base = Math.max(currentDue, now);
  const msExtended = Math.floor((amount / dailyRate) * DAY_MS);
  const newDue = paidOff ? (nowSec + RELEASE_HORIZON_SEC) * 1000 : base + msExtended;

  const paymentId = uuidv4();
  await db.batch([
    db.prepare(
      `INSERT INTO payments (id, account_id, amount, method, reference, recorded_by, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).bind(
      paymentId,
      accountId,
      amount,
      method,
      reference || null,
      recordedBy,
      Math.floor(now / 1000)
    ),
    db.prepare(
      `UPDATE accounts
          SET amount_paid = ?,
              next_payment_due = ?,
              locked_by_dealer = 0,
              release_approved = CASE WHEN ? THEN 1 ELSE COALESCE(release_approved, 0) END,
              release_approved_at = CASE WHEN ? THEN COALESCE(release_approved_at, ?) ELSE release_approved_at END,
              updated_at = ?
        WHERE id = ?`
    ).bind(
      newAmountPaid,
      newDue,
      paidOff ? 1 : 0,
      paidOff ? 1 : 0,
      nowSec,
      nowSec,
      accountId
    )
  ]);

  return { paymentId, newAmountPaid, newDue, paidOff };
}
