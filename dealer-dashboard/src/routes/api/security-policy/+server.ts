import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, getDb, getDealerSecurityPolicy, parseFrpAccountIds } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  const policy = await getDealerSecurityPolicy({ platform }, locals.dealer.id);
  return json(policy);
};

export const PUT: RequestHandler = async ({ locals, platform, request }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  const body = await request.json();
  const frpAccountIds = parseFrpAccountIds(body.frpAccountIds ?? body.frpAccountIdsCsv ?? '');
  if (frpAccountIds.length > 10) {
    return errorResponse('At most 10 EFRP Google account IDs are allowed', 400);
  }
  const nowSec = Math.floor(Date.now() / 1000);
  const db = getDb({ platform });
  await db.prepare('UPDATE dealers SET frp_account_ids = ?, security_policy_updated_at = ? WHERE id = ?')
    .bind(JSON.stringify(frpAccountIds), nowSec, locals.dealer.id)
    .run();
  const policy = await getDealerSecurityPolicy({ platform }, locals.dealer.id);
  return json(policy);
};
