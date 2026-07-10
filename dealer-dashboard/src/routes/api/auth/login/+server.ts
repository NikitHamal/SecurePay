import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, getJwtSecret, errorResponse } from '$lib/api/server';
import { verifyPassword, createToken } from '$lib/auth';

export const POST: RequestHandler = async ({ request, platform, cookies, url }) => {
  const body = await request.json();
  const { email, password } = body;
  const isWebClient = body.client === 'web';

  if (!email || !password) {
    return errorResponse('Email and password are required', 400);
  }

  const db = getDb({ platform });
  const result = await db.prepare(`
    SELECT id, name, email, password, role, agency_id, branch_id, is_approved
    FROM dealers WHERE lower(email) = lower(?)
  `).bind(email).first();

  if (!result) {
    return errorResponse('Invalid email or password', 401);
  }

  if (!verifyPassword(password, result.password as string)) {
    return errorResponse('Invalid email or password', 401);
  }

  if (Number(result.is_approved ?? 0) !== 1) {
    return errorResponse('Account is pending approval or disabled', 403);
  }

  const allowedRoles = new Set(['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN', 'AGENT']);
  const role = String(result.role ?? '');
  if (!allowedRoles.has(role)) {
    return errorResponse('Account role is invalid. Contact the system administrator.', 403);
  }

  const dealerData = {
    id: result.id as string,
    name: result.name as string,
    role: role as 'SUPER_ADMIN' | 'AGENCY_OWNER' | 'BRANCH_ADMIN' | 'AGENT',
    agencyId: (result.agency_id as string) || null,
    branchId: (result.branch_id as string) || null
  };

  const token = createToken(dealerData, getJwtSecret({ platform }));
  cookies.set('tb_session', token, {
    httpOnly: true,
    secure: url.protocol === 'https:',
    sameSite: 'strict',
    path: '/',
    maxAge: 8 * 60 * 60
  });

  return json({
    ...(isWebClient ? {} : { token }),
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
