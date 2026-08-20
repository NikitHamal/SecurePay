import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';
import { getDealerScopeFilter } from '$lib/auth';
import { logActivity } from '$lib/audit';

/** Runtime guard mirroring migrations/20260801_accountability.sql (device GPS columns). */
let ensureRegColumnsPromise: Promise<void> | null = null;
function ensureDeviceRegistrationColumns(db: ReturnType<typeof getDb>): Promise<void> {
  if (!ensureRegColumnsPromise) {
    ensureRegColumnsPromise = (async () => {
      const info = await db.prepare('PRAGMA table_info(devices)').all();
      const existing = new Set(info.results.map((row) => String((row as { name?: unknown }).name ?? '')));
      for (const [column, ddl] of [
        ['reg_lat', 'REAL'], ['reg_lng', 'REAL'], ['reg_accuracy', 'REAL'],
        ['product_model_id', 'TEXT'], ['assigned_to', 'TEXT'], ['assigned_at', 'INTEGER'], ['assigned_by', 'TEXT'],
        ['total_amount', 'INTEGER'], ['down_payment', 'INTEGER'], ['daily_rate', 'INTEGER'], ['term_days', 'INTEGER'],
        ['down_payment_status', 'TEXT']
      ] as Array<[string, string]>) {
        if (!existing.has(column)) {
          await db.prepare(`ALTER TABLE devices ADD COLUMN ${column} ${ddl}`).run();
        }
      }
      await db.prepare('CREATE TABLE IF NOT EXISTS product_models (id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE, model TEXT NOT NULL, description TEXT, total_amount INTEGER NOT NULL, down_payment INTEGER NOT NULL, daily_rate INTEGER NOT NULL, term_days INTEGER NOT NULL, created_by TEXT, is_active INTEGER DEFAULT 1, created_at INTEGER DEFAULT (unixepoch()), updated_at INTEGER DEFAULT (unixepoch()))').run();
    })().catch((error) => {
      ensureRegColumnsPromise = null;
      throw error;
    });
  }
  return ensureRegColumnsPromise;
}

function parseCoordinate(value: unknown, min: number, max: number): number | null {
  if (value == null || value === '') return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < min || parsed > max) return null;
  return parsed;
}

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const db = getDb({ platform });
  await ensureDeviceRegistrationColumns(db);

  // Agents see only devices assigned to them (or legacy devices they registered).
  // Admins/owners see inventory per tenant hierarchy.
  if (locals.dealer.role === 'AGENT') {
    const result = await db.prepare(`
      SELECT d.*, a.customer_name, a.created_at as sold_at, owner.name as registered_by_name,
             pm.name as product_name
      FROM devices d
      JOIN dealers owner ON owner.id = d.dealer_id
      LEFT JOIN accounts a ON d.id = a.device_id
      LEFT JOIN product_models pm ON pm.id = d.product_model_id
      WHERE (d.assigned_to = ? OR (d.assigned_to IS NULL AND d.dealer_id = ?))
      ORDER BY d.created_at DESC
    `).bind(locals.dealer.id, locals.dealer.id).all();
    const devices = result.results.map((row: any) => ({
      id: row.id as string,
      imei: row.imei as string,
      model: row.model as string,
      dealerId: row.dealer_id as string,
      status: row.status as string,
      createdAt: Number(row.created_at) * 1000,
      customerName: row.customer_name as string | null,
      soldAt: row.sold_at ? Number(row.sold_at) * 1000 : null,
      registeredByName: (row.registered_by_name as string | null) ?? null,
      registrationLat: row.reg_lat == null ? null : Number(row.reg_lat),
      registrationLng: row.reg_lng == null ? null : Number(row.reg_lng),
      registrationAccuracy: row.reg_accuracy == null ? null : Number(row.reg_accuracy),
      productModelId: row.product_model_id as string | null,
      productName: row.product_name as string | null,
      assignedTo: row.assigned_to as string | null,
      assignedAt: row.assigned_at ? Number(row.assigned_at) * 1000 : null,
      totalAmount: row.total_amount != null ? Number(row.total_amount) : null,
      downPayment: row.down_payment != null ? Number(row.down_payment) : null,
      dailyRate: row.daily_rate != null ? Number(row.daily_rate) : null,
      termDays: row.term_days != null ? Number(row.term_days) : null,
      downPaymentStatus: row.down_payment_status as string | null
    }));
    return json(devices);
  }

  const scope = getDealerScopeFilter(locals.dealer, 'owner');
  const result = await db.prepare(`
    SELECT d.*, a.customer_name, a.created_at as sold_at, owner.name as registered_by_name,
           pm.name as product_name, assigned.name as assigned_to_name
    FROM devices d
    JOIN dealers owner ON owner.id = d.dealer_id
    LEFT JOIN accounts a ON d.id = a.device_id
    LEFT JOIN product_models pm ON pm.id = d.product_model_id
    LEFT JOIN dealers assigned ON assigned.id = d.assigned_to
    WHERE ${scope.where}
    ORDER BY d.created_at DESC
  `).bind(...scope.params).all();

  const devices = result.results.map((row: any) => ({
    id: row.id as string,
    imei: row.imei as string,
    model: row.model as string,
    dealerId: row.dealer_id as string,
    status: row.status as string,
    createdAt: Number(row.created_at) * 1000,
    customerName: row.customer_name as string | null,
    soldAt: row.sold_at ? Number(row.sold_at) * 1000 : null,
    registeredByName: (row.registered_by_name as string | null) ?? null,
    registrationLat: row.reg_lat == null ? null : Number(row.reg_lat),
    registrationLng: row.reg_lng == null ? null : Number(row.reg_lng),
    registrationAccuracy: row.reg_accuracy == null ? null : Number(row.reg_accuracy),
    productModelId: row.product_model_id as string | null,
    productName: row.product_name as string | null,
    assignedTo: row.assigned_to as string | null,
    assignedToName: row.assigned_to_name as string | null,
    assignedAt: row.assigned_at ? Number(row.assigned_at) * 1000 : null,
    totalAmount: row.total_amount != null ? Number(row.total_amount) : null,
    downPayment: row.down_payment != null ? Number(row.down_payment) : null,
    dailyRate: row.daily_rate != null ? Number(row.daily_rate) : null,
    termDays: row.term_days != null ? Number(row.term_days) : null,
    downPaymentStatus: row.down_payment_status as string | null
  }));

  return json(devices);
};

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  // Per client requirement, agents do not create inventory directly — admin creates and assigns.
  if (locals.dealer.role === 'AGENT') {
    return errorResponse('Only admins can add devices. Your admin must assign IMEIs to you.', 403);
  }

  const body = await request.json().catch(() => null) as Record<string, unknown> | null;
  if (!body) return errorResponse('Invalid JSON', 400);
  const imei = String((body as any).imei ?? '').trim();
  const model = String((body as any).model ?? '').trim();
  const latitude = (body as any).latitude;
  const longitude = (body as any).longitude;
  const accuracy = (body as any).accuracy;
  const productModelId = (body as any).productModelId ? String((body as any).productModelId).trim() : null;
  let totalAmount = (body as any).totalAmount != null ? Number((body as any).totalAmount) : (body as any).total_amount != null ? Number((body as any).total_amount) : null;
  let downPayment = (body as any).downPayment != null ? Number((body as any).downPayment) : (body as any).down_payment != null ? Number((body as any).down_payment) : null;
  let dailyRate = (body as any).dailyRate != null ? Number((body as any).dailyRate) : (body as any).daily_rate != null ? Number((body as any).daily_rate) : null;
  let termDays = (body as any).termDays != null ? Number((body as any).termDays)  : (body as any).term_days != null ? Number((body as any).term_days) : null;
  const assignedTo = (body as any).assignedTo ? String((body as any).assignedTo).trim() : (body as any).assigned_to ? String((body as any).assigned_to).trim() : null;

  if (!imei || !model) {
    return errorResponse('IMEI and model are required', 400);
  }

  if (imei.length !== 15 || !/^\d{15}$/.test(imei)) {
    return errorResponse('IMEI must be exactly 15 digits', 400);
  }

  // Where the phone physically was when it was registered (anti-fraud).
  const regLat = parseCoordinate(latitude, -90, 90);
  const regLng = parseCoordinate(longitude, -180, 180);
  const regAccuracy = parseCoordinate(accuracy, 0, 100000);

  const db = getDb({ platform });
  await ensureDeviceRegistrationColumns(db);

  const existing = await db.prepare('SELECT id FROM devices WHERE imei = ?').bind(imei).first();

  if (existing) {
    return errorResponse('Device with this IMEI already exists', 409);
  }

  // Resolve pricing: prefer product model, fallback to explicit amounts.
  let resolvedModel: string | null = null;
  if (productModelId) {
    const pm = await db.prepare('SELECT * FROM product_models WHERE id = ?').bind(productModelId).first<any>();
    if (!pm) return errorResponse('Product model not found', 404);
    if (Number(pm.is_active) !== 1) return errorResponse('Product model is inactive', 409);
    resolvedModel = pm.id as string;
    totalAmount = Number(pm.total_amount);
    downPayment = Number(pm.down_payment);
    dailyRate = Number(pm.daily_rate);
    termDays = Number(pm.term_days);
    // If body model differs from product model, prefer product's canonical model but allow override.
  }

  // Admin must set pricing at creation — either via product or explicit.
  if ((totalAmount == null || dailyRate == null || termDays == null) && !productModelId) {
    return errorResponse('Pricing required: select a product model or supply totalAmount/dailyRate/termDays', 400);
  }
  if (totalAmount != null && (!Number.isSafeInteger(totalAmount) || totalAmount <= 0)) return errorResponse('totalAmount must be positive integer (pesewas)', 400);
  if (downPayment != null && (!Number.isSafeInteger(downPayment) || downPayment < 0 || (totalAmount != null && downPayment > totalAmount))) return errorResponse('Invalid downPayment', 400);
  if (dailyRate != null && (!Number.isSafeInteger(dailyRate) || dailyRate <= 0)) return errorResponse('Invalid dailyRate', 400);
  if (termDays != null && (!Number.isSafeInteger(termDays) || termDays <= 0)) return errorResponse('Invalid termDays', 400);

  if (downPayment == null) downPayment = 0;
  const id = uuidv4();
  const now = Math.floor(Date.now() / 1000);

  // Optional immediate assignment.
  let assignedToId: string | null = null;
  let assignedAt: number | null = null;
  if (assignedTo) {
    const agent = await db.prepare('SELECT id, role FROM dealers WHERE id = ?').bind(assignedTo).first<{ id: string; role: string }>();
    if (!agent) return errorResponse('Assigned agent not found', 404);
    if (agent.role !== 'AGENT') return errorResponse('Devices can only be assigned to AGENTs', 400);
    assignedToId = agent.id;
    assignedAt = now;
  }

  await db.prepare(
    'INSERT INTO devices (id, imei, model, dealer_id, status, created_at, reg_lat, reg_lng, reg_accuracy, product_model_id, assigned_to, assigned_at, assigned_by, total_amount, down_payment, daily_rate, term_days) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
  ).bind(id, imei, model, locals.dealer.id, 'in_stock', now, regLat, regLng, regAccuracy, resolvedModel, assignedToId, assignedAt, assignedToId ? locals.dealer.id : null, totalAmount, downPayment, dailyRate, termDays).run();

  await logActivity(db, {
    actor: locals.dealer,
    action: assignedToId ? 'DEVICE_REGISTERED_AND_ASSIGNED' : 'DEVICE_REGISTERED',
    details: assignedToId ? `Registered ${model} (${imei}) and assigned to ${assignedToId}` : `Registered ${model} (${imei}) into inventory`,
    imei,
    latitude: regLat,
    longitude: regLng
  });

  return json({
    id,
    imei,
    model,
    dealerId: locals.dealer.id,
    status: 'in_stock',
    createdAt: now * 1000,
    registrationLat: regLat,
    registrationLng: regLng,
    registrationAccuracy: regAccuracy,
    productModelId: resolvedModel,
    assignedTo: assignedToId,
    assignedAt: assignedAt ? assignedAt * 1000 : null,
    totalAmount, downPayment, dailyRate, termDays
  }, { status: 201 });
};