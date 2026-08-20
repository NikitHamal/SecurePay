import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { canManagePricing } from '$lib/auth';

export const PATCH: RequestHandler = async ({ locals, params, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (!canManagePricing(locals.dealer.role)) return errorResponse('Only admins can update product pricing', 403);
  const db = getDb({ platform });
  const existing = await db.prepare('SELECT * FROM product_models WHERE id = ?').bind(params.id).first<any>();
  if (!existing) return errorResponse('Product not found', 404);
  const body = await request.json().catch(() => null) as any;
  if (!body) return errorResponse('Invalid JSON', 400);

  const name = body.name != null ? String(body.name).trim() : existing.name as string;
  const model = body.model != null ? String(body.model).trim() : existing.model as string;
  const total = body.totalAmount != null ? Number(body.totalAmount ?? body.total_amount) : Number(existing.total_amount);
  const down = body.downPayment != null ? Number(body.downPayment ?? body.down_payment) : Number(existing.down_payment);
  const daily = body.dailyRate != null ? Number(body.dailyRate ?? body.daily_rate) : Number(existing.daily_rate);
  const term = body.termDays != null ? Number(body.termDays ?? body.term_days) : Number(existing.term_days);
  const description = body.description !== undefined ? (body.description ? String(body.description).slice(0, 500) : null) : existing.description;
  const isActive = body.isActive !== undefined ? (body.isActive ? 1 : 0) : Number(existing.is_active);

  if (!name || name.length < 2) return errorResponse('Invalid name', 400);
  if (!Number.isSafeInteger(total) || total <= 0) return errorResponse('Invalid totalAmount', 400);
  if (!Number.isSafeInteger(down) || down < 0 || down > total) return errorResponse('Invalid downPayment', 400);
  if (!Number.isSafeInteger(daily) || daily <= 0) return errorResponse('Invalid dailyRate', 400);
  if (!Number.isSafeInteger(term) || term <= 0) return errorResponse('Invalid termDays', 400);

  const now = Math.floor(Date.now() / 1000);
  await db.prepare(
    'UPDATE product_models SET name=?, model=?, description=?, total_amount=?, down_payment=?, daily_rate=?, term_days=?, is_active=?, updated_at=? WHERE id=?'
  ).bind(name, model, description, total, down, daily, term, isActive, now, params.id).run();
  return json({ id: params.id, name, model, description, totalAmount: total, downPayment: down, dailyRate: daily, termDays: term, isActive: isActive === 1, updatedAt: now * 1000 });
};

export const DELETE: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (!canManagePricing(locals.dealer.role)) return errorResponse('Only admins can delete products', 403);
  const db = getDb({ platform });
  const existing = await db.prepare('SELECT id FROM product_models WHERE id = ?').bind(params.id).first();
  if (!existing) return errorResponse('Product not found', 404);
  const assigned = await db.prepare('SELECT COUNT(*) as c FROM devices WHERE product_model_id = ?').bind(params.id).first<{ c: number }>();
  if ((assigned?.c ?? 0) > 0) return errorResponse('Cannot delete product with assigned devices. Deactivate instead.', 409);
  await db.prepare('DELETE FROM product_models WHERE id = ?').bind(params.id).run();
  return json({ success: true, id: params.id });
};

export const GET: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  const db = getDb({ platform });
  const row = await db.prepare('SELECT * FROM product_models WHERE id = ?').bind(params.id).first<any>();
  if (!row) return errorResponse('Not found', 404);
  return json({
    id: row.id, name: row.name, model: row.model, description: row.description,
    totalAmount: Number(row.total_amount), downPayment: Number(row.down_payment),
    dailyRate: Number(row.daily_rate), termDays: Number(row.term_days),
    isActive: Number(row.is_active) === 1,
    createdAt: Number(row.created_at) * 1000, updatedAt: Number(row.updated_at) * 1000
  });
};
