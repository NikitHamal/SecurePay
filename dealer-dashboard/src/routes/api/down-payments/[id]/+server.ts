import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { logActivity } from '$lib/audit';
import { v4 as uuidv4 } from 'uuid';

let ensurePromise: Promise<void> | null = null;
function ensure(db: ReturnType<typeof getDb>): Promise<void> {
  if (!ensurePromise) {
    ensurePromise = (async () => {
      await db.prepare(`CREATE TABLE IF NOT EXISTS down_payment_submissions (
        id TEXT PRIMARY KEY, account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
        device_id TEXT NOT NULL REFERENCES devices(id), agent_id TEXT NOT NULL REFERENCES dealers(id),
        amount INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','confirmed','rejected','cancelled')),
        method TEXT NOT NULL DEFAULT 'cash', reference TEXT, submitted_at INTEGER NOT NULL DEFAULT (unixepoch()),
        confirmed_by TEXT REFERENCES dealers(id), confirmed_at INTEGER, note TEXT,
        created_at INTEGER DEFAULT (unixepoch()), updated_at INTEGER DEFAULT (unixepoch())
      )`).run();
    })().catch(e=>{ ensurePromise=null; throw e; });
  }
  return ensurePromise;
}

export const POST: RequestHandler = async ({ locals, params, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (locals.dealer.role === 'AGENT') return errorResponse('Only admins can confirm/reject payments', 403);
  const body = await request.json().catch(() => null) as any;
  const action = String(body?.action ?? '').trim(); // confirm | reject
  const note = body?.note ? String(body.note).slice(0, 500) : null;
  if (!['confirm','reject'].includes(action)) return errorResponse('action must be confirm or reject', 400);
  const db = getDb({ platform });
  await ensure(db);
  const sub = await db.prepare(`
    SELECT s.*, a.total_loan_amount, a.amount_paid, a.enrolled_by, a.agency_id, a.branch_id, d.imei, d.model
    FROM down_payment_submissions s
    JOIN accounts a ON a.id = s.account_id
    JOIN devices d ON d.id = s.device_id
    WHERE s.id = ?
  `).bind(params.id).first<any>();
  if (!sub) return errorResponse('Submission not found', 404);
  if (sub.status !== 'pending') return errorResponse(`Submission already ${sub.status}`, 409);
  // Scope check for agency/branch admin.
  if (locals.dealer.role === 'AGENCY_OWNER' && sub.agency_id !== locals.dealer.agencyId) return errorResponse('Not in your agency', 403);
  if (locals.dealer.role === 'BRANCH_ADMIN' && sub.branch_id !== locals.dealer.branchId) return errorResponse('Not in your branch', 403);

  const now = Math.floor(Date.now() / 1000);
  if (action === 'confirm') {
    const amount = Number(sub.amount);
    await db.batch([
      db.prepare("UPDATE down_payment_submissions SET status='confirmed', confirmed_by=?, confirmed_at=?, updated_at=?, note=? WHERE id=?").bind(locals.dealer.id, now, now, note, params.id),
      db.prepare("UPDATE accounts SET amount_paid = amount_paid + ?, down_payment_status='confirmed', down_payment_confirmed_by=?, down_payment_confirmed_at=?, updated_at=? WHERE id=?").bind(amount, locals.dealer.id, now, now, sub.account_id),
      db.prepare("INSERT INTO payments (id, account_id, amount, method, reference, recorded_by, created_at) VALUES (?, ?, ?, 'cash', ?, ?, ?)").bind(uuidv4(), sub.account_id, amount, `Down payment confirmed (${sub.imei})`, locals.dealer.id, now)
    ]);
    await logActivity(db, { actor: locals.dealer!, action: 'DOWN_PAYMENT_CONFIRMED', details: `Confirmed GHS ${(amount/100).toFixed(2)} for ${sub.imei}`, accountId: sub.account_id, imei: sub.imei });
    // Notify agent
    await db.prepare("INSERT INTO notifications (id, recipient_id, type, title, message, related_entity_type, related_entity_id, created_at) VALUES (?, ?, 'DOWN_PAYMENT_CONFIRMED', 'Down payment confirmed', ?, 'account', ?, ?)")
      .bind(uuidv4(), sub.agent_id, `Your GHS ${(amount/100).toFixed(2)} down payment for ${sub.model} (${sub.imei}) was confirmed. Device can now be provisioned.`, sub.account_id, now).run();
  } else {
    await db.prepare("UPDATE down_payment_submissions SET status='rejected', confirmed_by=?, confirmed_at=?, updated_at=?, note=? WHERE id=?").bind(locals.dealer.id, now, now, note, params.id).run();
    await db.prepare("UPDATE accounts SET down_payment_status='rejected', updated_at=? WHERE id=?").bind(now, sub.account_id).run();
    await logActivity(db, { actor: locals.dealer!, action: 'DOWN_PAYMENT_REJECTED', details: `Rejected down payment for ${sub.imei}: ${note ?? ''}`, accountId: sub.account_id, imei: sub.imei });
    await db.prepare("INSERT INTO notifications (id, recipient_id, type, title, message, related_entity_type, related_entity_id, created_at) VALUES (?, ?, 'DOWN_PAYMENT_REJECTED', 'Down payment rejected', ?, 'account', ?, ?)")
      .bind(uuidv4(), sub.agent_id, `Down payment for ${sub.model} (${sub.imei}) was rejected${note ? ': ' + note : ''}. Please resubmit.`, sub.account_id, now).run();
  }
  const updated = await db.prepare('SELECT * FROM down_payment_submissions WHERE id=?').bind(params.id).first<any>();
  return json({ id: updated.id, status: updated.status, confirmedBy: updated.confirmed_by, confirmedAt: updated.confirmed_at ? Number(updated.confirmed_at)*1000 : null });
};

export const GET: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  const db = getDb({ platform });
  await ensure(db);
  const row = await db.prepare(`
    SELECT s.*, a.customer_name, d.imei, d.model, ag.name as agent_name
    FROM down_payment_submissions s
    JOIN accounts a ON a.id = s.account_id
    JOIN devices d ON d.id = s.device_id
    JOIN dealers ag ON ag.id = s.agent_id
    WHERE s.id = ?
  `).bind(params.id).first<any>();
  if (!row) return errorResponse('Not found', 404);
  // Agents can only see their own.
  if (locals.dealer.role === 'AGENT' && row.agent_id !== locals.dealer.id) return errorResponse('Forbidden', 403);
  return json({
    id: row.id, accountId: row.account_id, deviceId: row.device_id, agentId: row.agent_id, agentName: row.agent_name,
    customerName: row.customer_name, imei: row.imei, model: row.model,
    amount: Number(row.amount), status: row.status, method: row.method,
    submittedAt: Number(row.submitted_at)*1000, confirmedBy: row.confirmed_by, confirmedAt: row.confirmed_at ? Number(row.confirmed_at)*1000 : null,
    note: row.note
  });
};
