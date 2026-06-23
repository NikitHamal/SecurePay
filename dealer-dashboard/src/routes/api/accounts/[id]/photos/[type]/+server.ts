import { errorResponse, getDb, getR2 } from '$lib/api/server';
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

export const GET: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const { id, type } = params;
  if (type !== 'photo' && type !== 'id_front' && type !== 'id_back') {
    return errorResponse('Invalid photo type', 400);
  }

  const db = getDb({ platform });
  const account = await db.prepare('SELECT customer_photo_path, national_id_front_path, national_id_back_path FROM accounts WHERE id = ? AND dealer_id = ?')
    .bind(id, locals.dealer.id)
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
        'Cache-Control': 'public, max-age=31536000'
      }
    });
  } catch (err: any) {
    return errorResponse(err.message || 'Server error', 500);
  }
};
