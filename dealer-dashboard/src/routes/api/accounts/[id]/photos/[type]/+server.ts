import { errorResponse, getDb, getR2 } from '$lib/api/server';
import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getAccountScopeFilter } from '$lib/auth';

export const GET: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const { id, type } = params;
  if (type !== 'photo' && type !== 'id_front' && type !== 'id_back' && type !== 'signature') {
    return errorResponse('Invalid photo type', 400);
  }

  const db = getDb({ platform });
  const scope = getAccountScopeFilter(locals.dealer, 'a');
  // SELECT * keeps this route working even before the application-extras
  // migration runs on an existing database (columns read with ?? null).
  const account = await db.prepare(`SELECT a.* FROM accounts a WHERE a.id = ? AND ${scope.where}`)
    .bind(id, ...scope.params)
    .first<Record<string, unknown>>();

  if (!account) {
    return errorResponse('Account not found', 404);
  }

  const path = (type === 'photo'
    ? account.customer_photo_path
    : type === 'id_front'
      ? account.national_id_front_path
      : type === 'id_back'
        ? account.national_id_back_path
        : account.customer_signature_path) as string | null | undefined;

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
