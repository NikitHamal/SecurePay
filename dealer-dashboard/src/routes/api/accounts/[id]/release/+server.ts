import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse, releaseFields, releaseApproved, releaseHorizon } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';
import type { Customer, Status } from '$lib/types';

export const POST: RequestHandler = async ({ locals, params, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const body = await request.json().catch(() => ({}));
  const allowEarlyRelease = body?.allowEarlyRelease === true;
  const note = typeof body?.note === 'string' ? body.note.trim().slice(0, 160) : '';

  const db = getDb({ platform });
  const account = await db.prepare(`
    SELECT a.*, d.id AS device_id, d.imei, d.model AS device_model, COALESCE(p.name, 'Custom') AS plan_name
      FROM accounts a
      JOIN devices d ON a.device_id = d.id
      LEFT JOIN plans p ON a.plan_id = p.id
     WHERE a.id = ? AND a.dealer_id = ?
  `).bind(params.id, locals.dealer.id).first();

  if (!account) return errorResponse('Account not found', 404);

  const amountPaid = Number(account.amount_paid);
  const totalLoanAmount = Number(account.total_loan_amount);
  const remaining = Math.max(0, totalLoanAmount - amountPaid);
  if (remaining > 0 && !allowEarlyRelease) {
    return errorResponse('Loan is not fully paid. Pass allowEarlyRelease=true for a dealer-approved test/settlement release.', 409);
  }

  const now = Date.now();
  const nowSec = Math.floor(now / 1000);
  const releaseDue = releaseHorizon(now);

  await db.batch([
    db.prepare(
      `UPDATE accounts
          SET release_approved = 1,
              release_approved_at = COALESCE(release_approved_at, ?),
              locked_by_dealer = 0,
              next_payment_due = ?,
              updated_at = ?
        WHERE id = ? AND dealer_id = ?`
    ).bind(nowSec, releaseDue, nowSec, params.id, locals.dealer.id),
    db.prepare("INSERT INTO lock_events (id, account_id, event_type, triggered_by, created_at) VALUES (?, ?, 'release_approved', 'dealer', ?)")
      .bind(uuidv4(), params.id, nowSec)
  ]);

  if (note) {
    await db.prepare(
      "INSERT INTO lock_events (id, account_id, event_type, triggered_by, created_at) VALUES (?, ?, ?, 'dealer', ?)"
    ).bind(uuidv4(), params.id, `release_note:${note}`, nowSec).run();
  }

  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model AS device_model, COALESCE(p.name, 'Custom') AS plan_name
      FROM accounts a
      JOIN devices d ON a.device_id = d.id
      LEFT JOIN plans p ON a.plan_id = p.id
     WHERE a.id = ? AND a.dealer_id = ?
  `).bind(params.id, locals.dealer.id).first();

  if (!row) return errorResponse('Release approved but the account could not be reloaded', 500);

  const nextDue = Number(row.next_payment_due);
  const amtPaid = Number(row.amount_paid);
  const totalLoan = Number(row.total_loan_amount);
  const status: Status = releaseApproved(row as Record<string, unknown>) ? 'ACTIVE' : (row.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(nextDue));

  const customer: Customer = {
    id: row.id as string,
    customerName: row.customer_name as string,
    nationalId: row.national_id as string,
    phoneNumber: row.phone_number as string,
    imei: row.imei as string,
    deviceModel: row.device_model as string,
    planName: row.plan_name as string || 'Custom',
    totalLoanAmount: totalLoan,
    amountPaid: amtPaid,
    remainingBalance: Math.max(0, totalLoan - amtPaid),
    dailyRate: Number(row.daily_rate),
    nextPaymentDueEpochMillis: nextDue,
    status,
    termDays: Number(row.term_days),
    ...releaseFields(row as Record<string, unknown>)
  };

  return json(customer);
};
