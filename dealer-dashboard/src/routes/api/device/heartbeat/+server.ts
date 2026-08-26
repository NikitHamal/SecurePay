import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse, releaseFields, releaseApproved, getDealerSecurityPolicy, getPaystackSecret } from '$lib/api/server';
import { pushCustomerDevice, cedisLabel } from '$lib/notify';
import { requeryPendingPayments } from '$lib/paystack-sync';

/**
 * Customer trigger: "payment reminder". The managed app calls this endpoint
 * regularly — when the account is due within 24h (or already past due) we
 * push one reminder per account per day (UTC) via FCM.
 */
async function maybeSendPaymentReminder(db: ReturnType<typeof getDb>, platform: App.Platform | null | undefined, account: Record<string, unknown>, accountId: string, now: number): Promise<void> {
  const totalLoan = Number(account.total_loan_amount);
  const amountPaid = Number(account.amount_paid);
  const remaining = Math.max(0, totalLoan - amountPaid);
  const dueAt = Number(account.next_payment_due);
  if (remaining <= 0 || dueAt - now > 24 * 60 * 60 * 1000) return;

  try {
    await db.prepare(`
      CREATE TABLE IF NOT EXISTS payment_reminders (
        account_id TEXT NOT NULL,
        reminder_day TEXT NOT NULL,
        created_at INTEGER NOT NULL DEFAULT (unixepoch()),
        PRIMARY KEY (account_id, reminder_day)
      )
    `).run();

    const day = new Date(now).toISOString().slice(0, 10);
    const inserted = await db.prepare(
      'INSERT OR IGNORE INTO payment_reminders (account_id, reminder_day, created_at) VALUES (?, ?, ?)'
    ).bind(accountId, day, Math.floor(now / 1000)).run();
    if (Number(inserted.meta.changes ?? 0) !== 1) return; // already reminded today

    // Reclaim storage for reminders older than 30 days, amortized on sends.
    void db.prepare('DELETE FROM payment_reminders WHERE created_at < ?')
      .bind(Math.floor(now / 1000) - 30 * 86400).run().catch(() => {});

    const fcmToken = String(account.fcm_token ?? '').trim();
    if (!fcmToken) return;
    const dailyRate = Number(account.daily_rate);
    const dueLabel = new Date(dueAt).toLocaleDateString('en-GB', { day: '2-digit', month: 'long', year: 'numeric' });
    const body = now >= dueAt
      ? `Your payment of ${cedisLabel(dailyRate)} was due on ${dueLabel}. Pay now to keep your phone active. Remaining balance: ${cedisLabel(remaining)}.`
      : `Your payment of ${cedisLabel(dailyRate)} is due by ${dueLabel}. Remaining balance: ${cedisLabel(remaining)}.`;
    await pushCustomerDevice(platform?.env, fcmToken, 'Payment reminder', body, accountId);
  } catch (error) {
    console.error('payment reminder failed', error);
  }
}

export const POST: RequestHandler = async ({ request, platform, locals }) => {
  if (!locals.hmacVerified) {
    return errorResponse('HMAC verification required', 401);
  }

  const body = await request.json();
  const imei = String(body.imei ?? '').trim();
  const accountId = String(body.accountId ?? '').trim();

  if (!/^\d{15}$/.test(imei) || !accountId) {
    return errorResponse('A valid IMEI and accountId are required', 400);
  }

  const db = getDb({ platform });

  const device = await db.prepare('SELECT id, imei, model FROM devices WHERE imei = ?').bind(imei).first();

  if (!device) {
    return errorResponse('Device not found', 404);
  }

  const account = await db.prepare('SELECT * FROM accounts WHERE device_id = ? AND id = ?').bind(device.id as string, accountId).first();

  if (!account) {
    return json({
      enrolled: false,
      device: {
        id: device.id,
        imei: device.imei,
        model: device.model
      },
      serverTime: Date.now()
    });
  }

  const now = Date.now();
  await db.prepare('UPDATE accounts SET updated_at = ? WHERE id = ?').bind(Math.floor(now / 1000), account.id as string).run();

  // Self-heal: if the customer paid (MoMo prompt approved after a session closed,
  // or the webhook never fired), the heartbeat requeries Paystack and credits the
  // account before returning status — no manual dashboard sync needed.
  let credited = 0;
  try {
    const psSecret = getPaystackSecret({ platform });
    if (psSecret) {
      const synced = await requeryPendingPayments(db, psSecret, { accountId: String(account.id) });
      credited = synced.filter((r) => r.applied).length;
      if (credited > 0) {
        // Reload so the response reflects the credited balance immediately.
        const fresh = await db.prepare('SELECT * FROM accounts WHERE device_id = ? AND id = ?').bind(device.id as string, accountId).first();
        if (fresh) Object.assign(account as Record<string, unknown>, fresh);
        console.log('[heartbeat] credited', credited, 'pending Paystack payment(s) for', accountId);
      }
    }
  } catch (e) {
    console.error('[heartbeat] paystack sync failed', e);
  }

  const securityPolicy = await getDealerSecurityPolicy({ platform }, String(account.dealer_id));
  const release = releaseFields(account as Record<string, unknown>);
  const isStolen = Number(account.is_stolen ?? 0) === 1;

  if (!release.releaseApproved && !isStolen) {
    void maybeSendPaymentReminder(db, platform, account as Record<string, unknown>, accountId, now);
  }

  const status = releaseApproved(account as Record<string, unknown>)
    ? 'ACTIVE'
    : (isStolen ? 'STOLEN' : (account.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(Number(account.next_payment_due))));

  return json({
    enrolled: true,
    device: {
      id: device.id,
      imei: device.imei,
      model: device.model
    },
    account: {
      id: account.id,
      customerName: account.customer_name,
      status,
      nextPaymentDue: Number(account.next_payment_due),
      amountPaid: Number(account.amount_paid),
      totalLoanAmount: Number(account.total_loan_amount),
      dailyRate: Number(account.daily_rate),
      isStolen,
      releaseApproved: release.releaseApproved,
      releaseApprovedAt: release.releaseApprovedAt,
      releasedAt: release.releasedAt
    },
    creditedPendingPayments: credited,
    securityPolicy,
    serverTime: Date.now()
  });
};