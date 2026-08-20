import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

let ensureTable: Promise<void> | null = null;
function ensure(db: ReturnType<typeof getDb>): Promise<void> {
  if (!ensureTable) {
    ensureTable = (async () => {
      await db.prepare(`CREATE TABLE IF NOT EXISTS down_payment_submissions (
        id TEXT PRIMARY KEY, account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
        device_id TEXT NOT NULL REFERENCES devices(id), agent_id TEXT NOT NULL REFERENCES dealers(id),
        amount INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','confirmed','rejected','cancelled')),
        method TEXT NOT NULL DEFAULT 'cash', reference TEXT, submitted_at INTEGER NOT NULL DEFAULT (unixepoch()),
        confirmed_by TEXT REFERENCES dealers(id), confirmed_at INTEGER, note TEXT,
        created_at INTEGER DEFAULT (unixepoch()), updated_at INTEGER DEFAULT (unixepoch())
      )`).run();
      const info = await db.prepare('PRAGMA table_info(accounts)').all();
      const cols = new Set(info.results.map((r: any) => String(r.name)));
      for (const [c, ddl] of [['down_payment_status','TEXT'],['down_payment_confirmed_by','TEXT'],['down_payment_confirmed_at','INTEGER']] as const) {
        if (!cols.has(c)) await db.prepare(`ALTER TABLE accounts ADD COLUMN ${c} ${ddl}`).run();
      }
    })().catch(e=>{ ensureTable=null; throw e; });
  }
  return ensureTable;
}

export const GET: RequestHandler = async ({ locals, platform, url }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  const db = getDb({ platform });
  await ensure(db);
  const status = url.searchParams.get('status');
  const isAgent = locals.dealer.role === 'AGENT';
  let rows;
  if (isAgent) {
    rows = await db.prepare(`
      SELECT s.*, a.customer_name, d.imei, d.model, ag.name as agent_name
      FROM down_payment_submissions s
      JOIN accounts a ON a.id = s.account_id
      JOIN devices d ON d.id = s.device_id
      JOIN dealers ag ON ag.id = s.agent_id
      WHERE s.agent_id = ? ${status ? 'AND s.status = ?' : ''}
      ORDER BY s.submitted_at DESC LIMIT 100
    `).bind(...(status ? [locals.dealer.id, status] : [locals.dealer.id])).all();
  } else {
    // Admins see submissions in their scope (agency/branch) or all for super.
    let where = '1=1';
    const params: string[] = [];
    if (locals.dealer.role === 'AGENCY_OWNER') { where = 'a.agency_id = ?'; params.push(locals.dealer.agencyId || '__none__'); }
    else if (locals.dealer.role === 'BRANCH_ADMIN') { where = 'a.branch_id = ?'; params.push(locals.dealer.branchId || '__none__'); }
    if (status) { where += ' AND s.status = ?'; params.push(status); }
    rows = await db.prepare(`
      SELECT s.*, a.customer_name, d.imei, d.model, ag.name as agent_name, a.agency_id, a.branch_id
      FROM down_payment_submissions s
      JOIN accounts a ON a.id = s.account_id
      JOIN devices d ON d.id = s.device_id
      JOIN dealers ag ON ag.id = s.agent_id
      WHERE ${where}
      ORDER BY s.submitted_at DESC LIMIT 200
    `).bind(...params).all();
  }
  const mapped = rows.results.map((r: any) => ({
    id: r.id as string,
    accountId: r.account_id as string,
    deviceId: r.device_id as string,
    agentId: r.agent_id as string,
    agentName: r.agent_name as string | null,
    customerName: r.customer_name as string,
    imei: r.imei as string,
    model: r.model as string,
    amount: Number(r.amount),
    status: r.status as string,
    method: r.method as string,
    submittedAt: Number(r.submitted_at) * 1000,
    confirmedBy: r.confirmed_by as string | null,
    confirmedAt: r.confirmed_at ? Number(r.confirmed_at) * 1000 : null,
    createdAt: Number(r.created_at) * 1000
  }));
  return json(mapped);
};

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  // Agent submits a cash down payment that they physically collected.
  // Body: accountId, amount (pesewas), reference?
  const body = await request.json().catch(() => null) as any;
  const accountId = String(body?.accountId ?? '').trim();
  const amount = Number(body?.amount);
  const reference = body?.reference ? String(body.reference).slice(0, 120) : null;
  if (!accountId) return errorResponse('accountId required', 400);
  if (!Number.isSafeInteger(amount) || amount <= 0) return errorResponse('amount must be positive pesewas', 400);
  const db = getDb({ platform });
  await ensure(db);
  const account = await db.prepare('SELECT id, device_id, enrolled_by, total_loan_amount, down_payment FROM accounts WHERE id = ?').bind(accountId).first<any>();
  if (!account) return errorResponse('Account not found', 404);
  // Only the enrolling agent or agency admin can submit for this account.
  if (locals.dealer.role === 'AGENT' && account.enrolled_by !== locals.dealer.id) return errorResponse('You can only submit for your own customers', 403);
  const device = await db.prepare('SELECT id, model, imei FROM devices WHERE id = ?').bind(account.device_id).first<any>();
  if (!device) return errorResponse('Device not found', 404);
  // Prevent duplicate pending.
  const pending = await db.prepare("SELECT id FROM down_payment_submissions WHERE account_id = ? AND status = 'pending' LIMIT 1").bind(accountId).first();
  if (pending) return errorResponse('A pending submission already exists for this account', 409);
  const id = uuidv4();
  const now = Math.floor(Date.now() / 1000);
  await db.prepare(
    "INSERT INTO down_payment_submissions (id, account_id, device_id, agent_id, amount, status, method, reference, submitted_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'pending', 'cash', ?, ?, ?, ?)"
  ).bind(id, accountId, device.id, locals.dealer.id, amount, reference, now, now, now).run();
  // Mark account pending if it was unpaid.
  await db.prepare("UPDATE accounts SET down_payment_status = 'pending', updated_at = ? WHERE id = ? AND (down_payment_status IS NULL OR down_payment_status = 'unpaid')").bind(now, accountId).run();

  // Notify admins in scope.
  const admins = await db.prepare(`SELECT id FROM dealers WHERE role = 'SUPER_ADMIN' OR (role = 'AGENCY_OWNER' AND agency_id = (SELECT agency_id FROM accounts WHERE id = ?)) OR (role = 'BRANCH_ADMIN' AND branch_id = (SELECT branch_id FROM accounts WHERE id = ?))`).bind(accountId, accountId).all<{ id: string }>();
  const notifs = admins.results.filter(r => r.id !== locals.dealer!.id).map(r => db.prepare(
    "INSERT INTO notifications (id, recipient_id, type, title, message, related_entity_type, related_entity_id, created_at) VALUES (?, ?, 'DOWN_PAYMENT_PENDING', ?, ?, 'down_payment', ?, ?)"
  ).bind(uuidv4(), r.id, 'Down payment submitted', `${locals.dealer!.name} submitted GHS ${(amount/100).toFixed(2)} for ${device.imei}`, id, now));
  if (notifs.length) await db.batch(notifs);

  const row = await db.prepare('SELECT * FROM down_payment_submissions WHERE id = ?').bind(id).first<any>();
  return json({ id: row.id, accountId: row.account_id, amount: Number(row.amount), status: row.status, submittedAt: Number(row.submitted_at)*1000 }, { status: 201 });
};
