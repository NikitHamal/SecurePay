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
  const result = await db.prepare(`
    SELECT id, name, email, password, role, agency_id, branch_id
    FROM dealers WHERE email = ?
  `).bind(email).first();

  if (!result) {
    return errorResponse('Invalid email or password', 401);
  }

  if (!verifyPassword(password, result.password as string)) {
    return errorResponse('Invalid email or password', 401);
  }

  const dealerData = {
    id: result.id as string,
    name: result.name as string,
    role: (result.role as string) as any || 'SUPER_ADMIN',
    agencyId: (result.agency_id as string) || null,
    branchId: (result.branch_id as string) || null
  };

  const token = createToken(dealerData, getJwtSecret({ platform }));

  return json({
    token,
    dealer: {
      id: result.id,
      name: result.name,
      email: result.email,
      role: dealerData.role,
      agencyId: dealerData.agencyId,
      branchId: dealerData.branchId
    }
  });
};
