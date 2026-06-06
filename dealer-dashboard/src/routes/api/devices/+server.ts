import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { db } from '$lib/db';
import { errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const result = await db.execute({
    sql: 'SELECT * FROM devices WHERE dealer_id = ? ORDER BY created_at DESC',
    args: [locals.dealer.id]
  });

  const devices = result.rows.map((row) => ({
    id: row.id as string,
    imei: row.imei as string,
    model: row.model as string,
    dealerId: row.dealer_id as string,
    status: row.status as string,
    createdAt: Number(row.created_at) * 1000
  }));

  return json(devices);
};

export const POST: RequestHandler = async ({ locals, request }) => {
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

  const existing = await db.execute({
    sql: 'SELECT id FROM devices WHERE imei = ?',
    args: [imei]
  });

  if (existing.rows.length > 0) {
    return errorResponse('Device with this IMEI already exists', 409);
  }

  const { v4: uuidv4 } = await import('uuid');
  const id = uuidv4();
  const now = Math.floor(Date.now() / 1000);

  await db.execute({
    sql: 'INSERT INTO devices (id, imei, model, dealer_id, status, created_at) VALUES (?, ?, ?, ?, ?, ?)',
    args: [id, imei, model, locals.dealer.id, 'in_stock', now]
  });

  return json({ id, imei, model, dealerId: locals.dealer.id, status: 'in_stock', createdAt: now * 1000 }, { status: 201 });
};