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
      for (const [column, ddl] of [['reg_lat', 'REAL'], ['reg_lng', 'REAL'], ['reg_accuracy', 'REAL']] as Array<[string, string]>) {
        if (!existing.has(column)) {
          await db.prepare(`ALTER TABLE devices ADD COLUMN ${column} ${ddl}`).run();
        }
      }
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
  const scope = getDealerScopeFilter(locals.dealer, 'owner');
  const result = await db.prepare(`
    SELECT d.*, a.customer_name, a.created_at as sold_at, owner.name as registered_by_name
    FROM devices d
    JOIN dealers owner ON owner.id = d.dealer_id
    LEFT JOIN accounts a ON d.id = a.device_id
    WHERE ${scope.where}
    ORDER BY d.created_at DESC
  `).bind(...scope.params).all();

  const devices = result.results.map((row) => ({
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
    registrationAccuracy: row.reg_accuracy == null ? null : Number(row.reg_accuracy)
  }));

  return json(devices);
};

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const { imei, model, latitude, longitude, accuracy } = await request.json();

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

  const id = uuidv4();
  const now = Math.floor(Date.now() / 1000);

  await db.prepare(
    'INSERT INTO devices (id, imei, model, dealer_id, status, created_at, reg_lat, reg_lng, reg_accuracy) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
  ).bind(id, imei, model, locals.dealer.id, 'in_stock', now, regLat, regLng, regAccuracy).run();

  await logActivity(db, {
    actor: locals.dealer,
    action: 'DEVICE_REGISTERED',
    details: `Registered ${model} (${imei}) into inventory`,
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
    registrationAccuracy: regAccuracy
  }, { status: 201 });
};