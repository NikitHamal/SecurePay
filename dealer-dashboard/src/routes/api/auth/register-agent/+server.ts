import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse, generateToken } from '$lib/api/server';
import { hashPassword } from '$lib/auth';

export const POST: RequestHandler = async ({ request, platform }) => {
  const body = await request.json() as Record<string, unknown>;
  const fullName = String(body.fullName ?? '').trim().slice(0, 120);
  const email = String(body.email ?? '').trim().toLowerCase().slice(0, 254);
  const phone = String(body.phone ?? '').trim().slice(0, 32);
  const password = String(body.password ?? '');
  const requestedBranchId = String(body.requestedBranchId ?? '').trim();

  if (!fullName || !email || !phone || !password) return errorResponse('Missing required fields', 400);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return errorResponse('Valid email required', 400);
  if (password.length < 10 || password.length > 128) {
    return errorResponse('Password must be 10-128 characters', 400);
  }

  const db = getDb({ platform });
  if (requestedBranchId) {
    const branch = await db.prepare('SELECT id FROM branches WHERE id = ? AND is_active = 1')
      .bind(requestedBranchId).first();
    if (!branch) return errorResponse('Requested branch not found', 404);
  }

  const existingDealer = await db.prepare('SELECT id FROM dealers WHERE lower(email) = lower(?)')
    .bind(email).first();
  if (existingDealer) return errorResponse('Email already registered', 409);

  const existingRequest = await db.prepare(
    "SELECT id FROM agent_requests WHERE lower(email) = lower(?) AND status = 'PENDING'"
  ).bind(email).first();
  if (existingRequest) return errorResponse('Registration request already pending', 409);

  const hashedPassword = hashPassword(password);
  const requestId = `AREQ-${generateToken(8).toUpperCase()}`;
  await db.prepare(`
    INSERT INTO agent_requests (id, full_name, email, phone, password, requested_branch_id, status, created_at)
    VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
  `).bind(requestId, fullName, email, phone, hashedPassword, requestedBranchId || null, Math.floor(Date.now() / 1000)).run();

  return json({
    message: 'Registration submitted. An admin will review your request.',
    requestId
  }, { status: 201 });
};
