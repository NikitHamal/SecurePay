import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { canManagePricing } from '$lib/auth';
import { v4 as uuidv4 } from 'uuid';

let ensureProductTable: Promise<void> | null = null;
function ensureTable(db: ReturnType<typeof getDb>): Promise<void> {
  if (!ensureProductTable) {
    ensureProductTable = db.prepare(`
      CREATE TABLE IF NOT EXISTS product_models (
        id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE, model TEXT NOT NULL,
        description TEXT, total_amount INTEGER NOT NULL, down_payment INTEGER NOT NULL,
        daily_rate INTEGER NOT NULL, term_days INTEGER NOT NULL,
        created_by TEXT REFERENCES dealers(id), is_active INTEGER NOT NULL DEFAULT 1,
        created_at INTEGER NOT NULL DEFAULT (unixepoch()),
        updated_at INTEGER NOT NULL DEFAULT (unixepoch())
      )
    `).run().then(() => {}).catch((e) => { ensureProductTable = null; throw e; });
  }
  return ensureProductTable;
}

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  const db = getDb({ platform });
  await ensureTable(db);
  const result = await db.prepare('SELECT * FROM product_models ORDER BY created_at DESC').all();
  const rows = result.results.map((r: any) => ({
    id: r.id as string,
    name: r.name as string,
    model: r.model as string,
    description: r.description as string | null,
    totalAmount: Number(r.total_amount),
    downPayment: Number(r.down_payment),
    dailyRate: Number(r.daily_rate),
    termDays: Number(r.term_days),
    createdBy: r.created_by as string | null,
    isActive: Number(r.is_active) === 1,
    createdAt: Number(r.created_at) * 1000,
    updatedAt: Number(r.updated_at) * 1000
  }));
  return json(rows);
};

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (!canManagePricing(locals.dealer.role)) return errorResponse('Only admins can create product pricing', 403);
  const body = await request.json().catch(() => null) as any;
  if (!body) return errorResponse('Invalid JSON', 400);
  const name = String(body.name ?? body.model ?? '').trim();
  const model = String(body.model ?? body.name ?? '').trim();
  const total = Number(body.totalAmount ?? body.total_amount);
  const down = Number(body.downPayment ?? body.down_payment ?? 0);
  const daily = Number(body.dailyRate ?? body.daily_rate);
  const term = Number(body.termDays ?? body.term_days);
  const description = body.description ? String(body.description).slice(0, 500) : null;

  if (!name || name.length < 2 || name.length > 80) return errorResponse('Product name required (2-80 chars)', 400);
  if (!model || model.length < 2) return errorResponse('Device model required', 400);
  if (!Number.isSafeInteger(total) || total <= 0) return errorResponse('totalAmount must be a positive integer (pesewas)', 400);
  if (!Number.isSafeInteger(down) || down < 0 || down > total) return errorResponse('downPayment must be between 0 and totalAmount', 400);
  if (!Number.isSafeInteger(daily) || daily <= 0) return errorResponse('dailyRate must be a positive integer (pesewas)', 400);
  if (!Number.isSafeInteger(term) || term <= 0) return errorResponse('termDays must be a positive integer', 400);

  const db = getDb({ platform });
  await ensureTable(db);
  const id = uuidv4();
  const now = Math.floor(Date.now() / 1000);
  try {
    await db.prepare(
      'INSERT INTO product_models (id, name, model, description, total_amount, down_payment, daily_rate, term_days, created_by, is_active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)'
    ).bind(id, name, model, description, total, down, daily, term, locals.dealer.id, now, now).run();
  } catch (e: any) {
    const msg = String(e?.message ?? '');
    if (msg.toLowerCase().includes('unique')) return errorResponse('A product with this name already exists', 409);
    throw e;
  }
  return json({ id, name, model, description, totalAmount: total, downPayment: down, dailyRate: daily, termDays: term, isActive: true, createdAt: now * 1000, updatedAt: now * 1000 }, { status: 201 });
};
