/**
 * Paystack requery sweep — self-healing payment reconciliation.
 *
 * Ghana MoMo charges via /charge return 'pay_offline': the USSD session ends
 * BEFORE the customer approves the PIN prompt, so nothing polls the result and
 * the webhook is a single point of failure. This sweep requeries Paystack for
 * unapplied transactions and credits accounts idempotently (payment_id guard),
 * so money that reached Paystack always lands on the ledger.
 */
import type { D1Database } from '@cloudflare/workers-types';
import { verifyTransaction } from '$lib/paystack';
import { applyPayment } from '$lib/payments';

export interface RequeryResult {
  reference: string;
  paystackStatus: string;
  applied: boolean;
  amount: number | null;
  error?: string;
}

const ACTIVE_STATUSES = "('pending','pay_offline','send_otp','otp_sent','processing','ongoing','timeout')";
const DEFAULT_MAX_AGE_SEC = 48 * 60 * 60;
const MAX_ROWS = 10;

export interface RequeryOptions {
  reference?: string;
  /** Local-format MoMo phone stored on the transaction (0XXXXXXXXX). */
  phone?: string;
  accountId?: string;
  /** Only sweep transactions enrolled by this dealer (agent scope). */
  enrolledBy?: string;
  maxAgeSec?: number;
}

export async function requeryPendingPayments(
  db: D1Database,
  secret: string,
  opts: RequeryOptions = {}
): Promise<RequeryResult[]> {
  const clauses: string[] = ['pt.payment_id IS NULL', `pt.status IN ${ACTIVE_STATUSES}`];
  const params: unknown[] = [];
  clauses.push('pt.created_at > unixepoch() - ?');
  params.push(opts.maxAgeSec ?? DEFAULT_MAX_AGE_SEC);
  if (opts.reference) { clauses.push('pt.reference = ?'); params.push(opts.reference); }
  if (opts.phone) { clauses.push('pt.customer_phone = ?'); params.push(opts.phone); }
  if (opts.accountId) { clauses.push('pt.account_id = ?'); params.push(opts.accountId); }
  if (opts.enrolledBy) { clauses.push('a.enrolled_by = ?'); params.push(opts.enrolledBy); }

  const join = opts.enrolledBy ? 'JOIN accounts a ON a.id = pt.account_id' : '';
  const rows = await db.prepare(`
    SELECT pt.reference, pt.account_id, pt.amount, pt.channel
      FROM paystack_transactions pt
      ${join}
     WHERE ${clauses.join(' AND ')}
     ORDER BY pt.created_at DESC
     LIMIT ${MAX_ROWS}
  `).bind(...params).all<{ reference: string; account_id: string; amount: number; channel: string | null }>();

  const results: RequeryResult[] = [];
  for (const row of rows.results ?? []) {
    const reference = String(row.reference);
    try {
      const verified = await verifyTransaction(reference, secret);
      if (verified.status === 'success') {
        const amount = Number(verified.amount || row.amount || 0);
        const channel = String(verified.channel || row.channel || 'mobile_money').toUpperCase();
        const { paymentId } = await applyPayment({
          db,
          accountId: String(row.account_id),
          amount,
          method: channel === 'CARD' ? 'CARD' : 'MOBILE_MONEY',
          reference: `paystack:${reference}`,
          recordedBy: 'paystack-sync'
        });
        const paidAt = verified.paid_at
          ? Math.floor(new Date(verified.paid_at).getTime() / 1000)
          : Math.floor(Date.now() / 1000);
        await db.prepare(`
          UPDATE paystack_transactions
             SET id = COALESCE(?, id),
                 status = 'success',
                 gateway_response = 'Successful',
                 authorization_code = ?,
                 channel = ?,
                 fees = ?,
                 paid_at = ?,
                 payment_id = ?,
                 updated_at = unixepoch()
           WHERE reference = ?
        `).bind(
          verified.id || null,
          verified.authorization?.authorization_code || null,
          verified.channel || null,
          verified.fees != null ? Math.round(Number(verified.fees)) : null,
          paidAt,
          paymentId,
          reference
        ).run();
        results.push({ reference, paystackStatus: 'success', applied: true, amount });
      } else if (['failed', 'abandoned', 'reversed'].includes(verified.status)) {
        await db.prepare(
          'UPDATE paystack_transactions SET status = ?, updated_at = unixepoch() WHERE reference = ? AND payment_id IS NULL'
        ).bind(verified.status, reference).run();
        results.push({ reference, paystackStatus: verified.status, applied: false, amount: Number(row.amount) });
      } else {
        results.push({ reference, paystackStatus: verified.status, applied: false, amount: Number(row.amount) });
      }
    } catch (err) {
      results.push({
        reference,
        paystackStatus: 'error',
        applied: false,
        amount: Number(row.amount),
        error: err instanceof Error ? err.message : String(err)
      });
    }
  }
  return results;
}
