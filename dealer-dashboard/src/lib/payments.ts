/**
 * Shared payment application logic used by both manual payments recorded in
 * the dashboard and Paystack webhook-driven payments.
 *
 * All monetary amounts are in pesewas (GHS subunit).
 */
import type { D1Database, D1PreparedStatement } from '@cloudflare/workers-types';
import { v4 as uuidv4 } from 'uuid';
import { notifyPaymentSuccess, type PushEnv } from '$lib/notify';
import { ensureDownPaymentSchema } from '$lib/api/server';

const DAY_MS = 24 * 60 * 60 * 1000;

const RELEASE_HORIZON_SEC = 10 * 365 * 24 * 60 * 60;

export interface ApplyPaymentInput {
  db: D1Database;
  accountId: string;
  amount: number;          // pesewas
  method: string;          // e.g. 'MOBILE_MONEY', 'CARD', 'CASH', 'BANK'
  reference?: string;
  recordedBy: string;      // dealer id or 'paystack'
  /** Platform env (FCM credentials) — when present, fires the customer/agent triggers. */
  env?: PushEnv;
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

  // The down-payment table must exist before any payment (an agent-cash batch
  // or a webhook) writes a submission into it on a first-time database.
  await ensureDownPaymentSchema(db);

  const account = await db.prepare(
    `SELECT * FROM accounts WHERE id = ?`
  ).bind(accountId).first<Record<string, unknown>>();
  if (!account) throw new Error('Account not found');

  const currentPaid = Number(account.amount_paid) || 0;
  const totalLoan = Number(account.total_loan_amount) || 0;
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

  // First-payment-as-down-payment rule: until the required down payment is
  // settled, every payment accumulates toward it. The account only unlocks once
  // amount_paid covers down_payment (or an admin confirmed an agent's cash
  // submission), so partial payments keep the phone locked.
  const dpRequired = Number(account.down_payment ?? 0) || 0;
  const dpStatus = String(account.down_payment_status ?? '').trim().toUpperCase();
  const dpSettledBefore = dpRequired <= 0 || dpStatus === 'CONFIRMED' || currentPaid >= dpRequired;
  const settlesDownPayment = dpRequired > 0 && !dpSettledBefore && newAmountPaid >= dpRequired;

  // A payment source is trusted to auto-confirm a down payment when the money
  // went through Paystack (system channels) or was recorded by a non-agent.
  const SYSTEM_SOURCES = new Set(['paystack', 'paystack-sync', 'ussd', 'web-portal', 'customer-app']);
  const recordedByKey = String(recordedBy ?? '').trim();
  let recorderIsAgent = false;
  if (recordedByKey && !SYSTEM_SOURCES.has(recordedByKey)) {
    const dealer = await db.prepare('SELECT role FROM dealers WHERE id = ?')
      .bind(recordedByKey).first<{ role?: string }>();
    if (dealer) recorderIsAgent = String(dealer.role ?? '').toUpperCase() === 'AGENT';
  }
  const trustedSource = Boolean(recordedByKey) && !recorderIsAgent;

  const paymentId = uuidv4();
  const statements: D1PreparedStatement[] = [
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
      nowSec
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
  ];

  // An agent's cash toward an unsettled down payment lands on the ledger but
  // cannot self-confirm: surface it as a pending submission so an admin can
  // verify and confirm (or Paystack settlement auto-confirms it later).
  if (dpRequired > 0 && !dpSettledBefore && recorderIsAgent) {
    statements.push(db.prepare(
      `INSERT INTO down_payment_submissions
         (id, account_id, device_id, agent_id, amount, status, method, reference, submitted_at, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, 'pending', ?, ?, ?, ?, ?)`
    ).bind(
      uuidv4(),
      accountId,
      String(account.device_id ?? ''),
      recordedByKey,
      amount,
      String(method ?? 'cash'),
      `payment:${paymentId}`,
      nowSec,
      nowSec,
      nowSec
    ));
  }

  // Verified money reached the down-payment threshold: confirm it so the phone
  // unlocks (no admin needed online). Idempotent — a one-shot transition.
  if (settlesDownPayment && trustedSource) {
    const confirmBy = SYSTEM_SOURCES.has(recordedByKey) ? null : recordedByKey;
    statements.push(
      db.prepare(
        `UPDATE down_payment_submissions
            SET status = 'confirmed', confirmed_by = ?, confirmed_at = ?, updated_at = ?
          WHERE account_id = ? AND status = 'pending'`
      ).bind(confirmBy, nowSec, nowSec, accountId),
      db.prepare(
        `UPDATE accounts
            SET down_payment_status = 'confirmed',
                down_payment_confirmed_by = ?,
                down_payment_confirmed_at = ?
          WHERE id = ? AND down_payment_status != 'confirmed'`
      ).bind(confirmBy, nowSec, accountId)
    );

    // Notify the submitting agent(s) that their collection was auto-confirmed.
    try {
      const subs = await db.prepare(
        `SELECT agent_id FROM down_payment_submissions
          WHERE account_id = ? AND status = 'pending'`
      ).bind(accountId).all<{ agent_id: string }>();
      for (const sub of subs.results ?? []) {
        statements.push(db.prepare(
          `INSERT INTO notifications (id, recipient_id, type, title, message, related_entity_type, related_entity_id, created_at)
           VALUES (?, ?, 'DOWN_PAYMENT_CONFIRMED', 'Down payment confirmed', ?, 'account', ?, ?)`
        ).bind(
          uuidv4(),
          sub.agent_id,
          `Your down payment for this account has been auto-confirmed after Paystack settlement. The phone can now be provisioned.`,
          accountId,
          nowSec
        ));
      }
    } catch (error) {
      // Notifications are best-effort; never fail the payment for them.
      console.error('[applyPayment] down-payment confirmation notify failed', error);
    }
  }

  await db.batch(statements);

  // Automated triggers: customer payment confirmation push + agent alert.
  await notifyPaymentSuccess(db, input.env, { accountId, paymentId, amount, recordedBy });

  return { paymentId, newAmountPaid, newDue, paidOff };
}
