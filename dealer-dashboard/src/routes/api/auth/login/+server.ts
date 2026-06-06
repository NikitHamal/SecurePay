import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { db } from '$lib/db';
import { verifyPassword, createToken } from '$lib/auth';
import { errorResponse } from '$lib/api/server';

export const POST: RequestHandler = async ({ request }) => {
  const body = await request.json();
  const { email, password } = body;

  if (!email || !password) {
    return errorResponse('Email and password are required', 400);
  }

  const result = await db.execute({
    sql: 'SELECT id, name, email, password FROM dealers WHERE email = ?',
    args: [email]
  });

  if (result.rows.length === 0) {
    return errorResponse('Invalid email or password', 401);
  }

  const dealer = result.rows[0];
  if (!verifyPassword(password, dealer.password as string)) {
    return errorResponse('Invalid email or password', 401);
  }

  const token = createToken(dealer.id as string, dealer.name as string);

  return json({
    token,
    dealer: {
      id: dealer.id,
      name: dealer.name,
      email: dealer.email
    }
  });
};