import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse, generateToken } from '$lib/api/server';
import { hashPassword } from '$lib/auth';

export const POST: RequestHandler = async ({ request, platform }) => {
  const body = await request.json();
  const { fullName, email, phone, password, requestedBranchId } = body;

  if (!fullName || !email || !phone || !password) {
    return errorResponse('Missing required fields', 400);
  }

  if (password.length < 8) {
    return errorResponse('Password must be at least 8 characters', 400);
  }

  const db = getDb({ platform });

  const existingDealer = await db.prepare('SELECT id FROM dealers WHERE email = ?').bind(email).first();
  if (existingDealer) {
    return errorResponse('Email already registered', 409);
  }

  const existingRequest = await db.prepare('SELECT id FROM agent_requests WHERE email = ? AND status = ?')
    .bind(email, 'PENDING').first();
  if (existingRequest) {
    return errorResponse('Registration request already pending', 409);
  }

  const hashedPassword = hashPassword(password);
  const requestId = `AREQ-${generateToken(4).toUpperCase()}`;

  await db.prepare(`
    INSERT INTO agent_requests (id, full_name, email, phone, password, requested_branch_id, status, created_at)
    VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
  `).bind(requestId, fullName, email, phone, hashedPassword, requestedBranchId || null, Math.floor(Date.now() / 1000)).run();

  return json({
    message: 'Registration submitted. An admin will review your request.',
    requestId
  }, { status: 201 });
};
