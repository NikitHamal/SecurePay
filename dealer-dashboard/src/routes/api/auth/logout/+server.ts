import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse } from '$lib/api/server';

export const POST: RequestHandler = async ({ locals }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  return json({ success: true });
};