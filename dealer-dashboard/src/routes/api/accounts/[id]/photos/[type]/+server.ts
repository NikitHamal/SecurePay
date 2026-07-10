import { errorResponse, getDb, getR2 } from '$lib/api/server';
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getAccountScopeFilter } from '$lib/auth';

export const GET: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const { id, type } = params;
  if (type !== 'photo' && type !== 'id_front' && type !== 'id_back') {
    return errorResponse('Invalid photo type', 400);
  }

  const db = getDb({ platform });
  const scope = getAccountScopeFilter(locals.dealer, 'a');
  const account = await db.prepare(`SELECT a.customer_photo_path, a.national_id_front_path, a.national_id_back_path FROM accounts a WHERE a.id = ? AND ${scope.where}`)
    .bind(id, ...scope.params)
    .first<{ customer_photo_path?: string | null; national_id_front_path?: string | null; national_id_back_path?: string | null }>();

  if (!account) {
    return errorResponse('Account not found', 404);
  }

  const path = type === 'photo' 
    ? account.customer_photo_path 
    : type === 'id_front' 
      ? account.national_id_front_path 
      : account.national_id_back_path;

  if (!path) {
    return errorResponse('Photo not uploaded', 404);
  }

  try {
    const r2 = getR2({ platform });
    const obj = await r2.get(path);
    if (!obj) {
      return errorResponse('File not found in storage', 404);
    }

    const bytes = await obj.arrayBuffer();
    return new Response(bytes, {
      headers: {
        'Content-Type': 'image/jpeg',
        'Cache-Control': 'private, max-age=300',
        'X-Content-Type-Options': 'nosniff'
      }
    });
  } catch (err: any) {
    return errorResponse(err.message || 'Server error', 500);
  }
};
