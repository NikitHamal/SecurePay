import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const db = getDb({ platform });
  const result = await db.prepare(`
    SELECT d.*, a.customer_name, a.created_at as sold_at
    FROM devices d
    LEFT JOIN accounts a ON d.id = a.device_id
    WHERE d.dealer_id = ?
    ORDER BY d.created_at DESC
  `).bind(locals.dealer.id).all();

  const devices = result.results.map((row) => ({
    id: row.id as string,
    imei: row.imei as string,
    model: row.model as string,
    dealerId: row.dealer_id as string,
    status: row.status as string,
    createdAt: Number(row.created_at) * 1000,
    customerName: row.customer_name as string | null,
    soldAt: row.sold_at ? Number(row.sold_at) * 1000 : null
  }));

  return json(devices);
};

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const { imei, model } = await request.json();

  if (!imei || !model) {
    return errorResponse('IMEI and model are required', 400);
  }

  if (imei.length !== 15 || !/^\d{15}$/.test(imei)) {
    return errorResponse('IMEI must be exactly 15 digits', 400);
  }

  const db = getDb({ platform });

  const existing = await db.prepare('SELECT id FROM devices WHERE imei = ?').bind(imei).first();

  if (existing) {
    return errorResponse('Device with this IMEI already exists', 409);
  }

  const id = uuidv4();
  const now = Math.floor(Date.now() / 1000);

  await db.prepare('INSERT INTO devices (id, imei, model, dealer_id, status, created_at) VALUES (?, ?, ?, ?, ?, ?)').bind(id, imei, model, locals.dealer.id, 'in_stock', now).run();

  return json({ id, imei, model, dealerId: locals.dealer.id, status: 'in_stock', createdAt: now * 1000 }, { status: 201 });
};