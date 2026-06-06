import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, getJwtSecret, errorResponse } from '$lib/api/server';
import { verifyPassword, createToken } from '$lib/auth';

export const POST: RequestHandler = async ({ request, platform }) => {
  const body = await request.json();
  const { email, password } = body;

  if (!email || !password) {
    return errorResponse('Email and password are required', 400);
  }

  const db = getDb({ platform });
  const result = await db.prepare('SELECT id, name, email, password FROM dealers WHERE email = ?').bind(email).first();

  if (!result) {
    return errorResponse('Invalid email or password', 401);
  }

  if (!verifyPassword(password, result.password as string)) {
    return errorResponse('Invalid email or password', 401);
  }

  const token = createToken(result.id as string, result.name as string, getJwtSecret({ platform }));

  return json({
    token,
    dealer: {
      id: result.id,
      name: result.name,
      email: result.email
    }
  });
};