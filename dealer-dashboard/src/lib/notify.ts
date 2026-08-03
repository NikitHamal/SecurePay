import type { D1Database } from '@cloudflare/workers-types';
import { v4 as uuidv4 } from 'uuid';
import { sendFcm } from '$lib/api/fcm';

/**
 * Notification plumbing for the automated triggers the client specified:
 *
 *   Customer  → payment reminder, successful payment, device warning
 *   Agent     → application approved, customer payment received
 *   Admin     → new customer registration (NEW_SALE, written at enrollment),
 *               default (overdue) alerts
 *
 * Delivery channels:
 *   - In-app notification rows (notifications table) for dealers/agents.
 *   - FCM data messages of type 'notification' for customer devices — the
 *     customer app renders these as local system notifications with no app
 *     update required.
 */

export interface PushEnv {
  FCM_SERVICE_ACCOUNT_EMAIL?: string;
  FCM_SERVICE_ACCOUNT_PRIVATE_KEY?: string;
  FCM_PROJECT_ID?: string;
}

/** Human readable cedis (amounts are pesewas). */
export function cedisLabel(pesewas: number): string {
  return `GH₵ ${(pesewas / 100).toLocaleString('en-GH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

/** Insert one in-app notification row for a dealer/agent. Never throws. */
export async function insertNotification(
  db: D1Database,
  input: {
    recipientId: string;
    type: string;
    title: string;
    message: string;
    relatedEntityType?: string | null;
    relatedEntityId?: string | null;
  }
): Promise<void> {
  try {
    await db.prepare(`
      INSERT INTO notifications (
        id, recipient_id, type, title, message, related_entity_type, related_entity_id, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      uuidv4(),
      input.recipientId,
      input.type.slice(0, 40),
      input.title.slice(0, 160),
      input.message.slice(0, 500),
      input.relatedEntityType ?? null,
      input.relatedEntityId ?? null,
      Math.floor(Date.now() / 1000)
    ).run();
  } catch (error) {
    console.error('Failed to insert notification', error);
  }
}

/** Fire-and-forget system notification onto a single customer device. */
export async function pushCustomerDevice(
  env: PushEnv | undefined,
  fcmToken: string,
  title: string,
  body: string,
  accountId?: string
): Promise<void> {
  const token = fcmToken.trim();
  if (!env || !token) return;
  try {
    await sendFcm(
      token,
      { type: 'notification', title, body, ...(accountId ? { accountId } : {}) },
      env
    );
  } catch (error) {
    console.error('Customer push failed', error);
  }
}

/**
 * After a payment is applied: confirm it to the customer device and, when the
 * payer was not the enrolling agent, alert the agent that money arrived.
 * `recordedBy` is a dealer id for manual payments and 'paystack' /
 * 'customer-app' for self-service MoMo.
 */
export async function notifyPaymentSuccess(
  db: D1Database,
  env: PushEnv | undefined,
  input: { accountId: string; paymentId: string; amount: number; recordedBy: string }
): Promise<void> {
  try {
    const account = await db.prepare(`
      SELECT a.customer_name, a.amount_paid, a.total_loan_amount, a.fcm_token, a.enrolled_by
        FROM accounts a WHERE a.id = ?
    `).bind(input.accountId).first<{
      customer_name: string;
      amount_paid: number;
      total_loan_amount: number;
      fcm_token: string | null;
      enrolled_by: string | null;
    }>();
    if (!account) return;

    const outstanding = Math.max(0, Number(account.total_loan_amount) - Number(account.amount_paid));
    const fcmToken = String(account.fcm_token ?? '').trim();
    if (fcmToken) {
      const body = outstanding <= 0
        ? `We have received your payment of ${cedisLabel(input.amount)}. Your loan is now fully paid — thank you!`
        : `We have received your payment of ${cedisLabel(input.amount)}. Remaining balance: ${cedisLabel(outstanding)}.`;
      await pushCustomerDevice(env, fcmToken, 'Payment received', body, input.accountId);
    }

    // "Customer payment received" → the agent who enrolled the account,
    // unless they are the one who just recorded it manually.
    const enrolledBy = String(account.enrolled_by ?? '').trim();
    if (enrolledBy && enrolledBy !== input.recordedBy) {
      // De-dupe per payment (the Paystack webhook and the app poll can race).
      const existing = await db.prepare(
        "SELECT id FROM notifications WHERE type = 'PAYMENT_RECEIVED' AND related_entity_id = ?"
      ).bind(input.paymentId).first<{ id: string }>();
      if (!existing) {
        await insertNotification(db, {
          recipientId: enrolledBy,
          type: 'PAYMENT_RECEIVED',
          title: 'Customer payment received',
          message: `${account.customer_name} paid ${cedisLabel(input.amount)} via Mobile Money. Remaining balance: ${cedisLabel(outstanding)}.`,
          relatedEntityType: 'payment',
          relatedEntityId: input.paymentId
        });
      }
    }
  } catch (error) {
    console.error('notifyPaymentSuccess failed', error);
  }
}

/**
 * Admin "default alerts": when an admin-role dealer loads their notification
 * centre, surface accounts that are past due and not released. De-duped per
 * account per UTC day so the feed cannot fill with repeats.
 */
export async function generateDefaulterAlerts(
  db: D1Database,
  dealer: { id: string; role: string; agencyId?: string | null; branchId?: string | null }
): Promise<void> {
  if (!['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN'].includes(dealer.role)) return;

  const scope =
    dealer.role === 'AGENCY_OWNER'
      ? { where: 'a.agency_id = ?', params: [dealer.agencyId || '__missing_agency__'] }
      : dealer.role === 'BRANCH_ADMIN'
        ? { where: 'a.branch_id = ?', params: [dealer.branchId || '__missing_branch__'] }
        : { where: '1=1', params: [] };

  try {
    const nowSec = Math.floor(Date.now() / 1000);
    const dayStart = nowSec - (nowSec % 86400);
    const overdue = await db.prepare(`
      SELECT a.id, a.customer_name, a.next_payment_due, d.name AS agent_name
        FROM accounts a
        LEFT JOIN dealers d ON d.id = a.enrolled_by
       WHERE a.next_payment_due < ?
         AND COALESCE(a.release_approved, 0) = 0
         AND COALESCE(a.is_stolen, 0) = 0
         AND a.amount_paid < a.total_loan_amount
         AND ${scope.where}
       ORDER BY a.next_payment_due ASC
       LIMIT 25
    `).bind(Date.now(), ...scope.params).all<{
      id: string;
      customer_name: string;
      next_payment_due: number;
      agent_name: string | null;
    }>();

    for (const row of overdue.results) {
      const existing = await db.prepare(`
        SELECT id FROM notifications
         WHERE recipient_id = ? AND type = 'DEFAULT_ALERT' AND related_entity_id = ? AND created_at >= ?
      `).bind(dealer.id, row.id, dayStart).first<{ id: string }>();
      if (existing) continue;

      const daysLate = Math.max(1, Math.ceil((Date.now() - Number(row.next_payment_due)) / 86400000));
      await insertNotification(db, {
        recipientId: dealer.id,
        type: 'DEFAULT_ALERT',
        title: 'Payment overdue',
        message: `${row.customer_name} is ${daysLate} day${daysLate === 1 ? '' : 's'} past due${row.agent_name ? ` (agent: ${row.agent_name})` : ''}.`,
        relatedEntityType: 'account',
        relatedEntityId: row.id
      });
    }
  } catch (error) {
    console.error('generateDefaulterAlerts failed', error);
  }
}
