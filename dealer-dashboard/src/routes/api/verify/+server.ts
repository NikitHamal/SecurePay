import type { RequestHandler } from './$types';
import { errorResponse } from '$lib/api/server';

/**
 * Deprecated unscoped endpoint retained to make old clients fail safely.
 * Use POST /api/accounts/:id/verify-ghana-card, which enforces hierarchy scope.
 */
export const POST: RequestHandler = async () => {
  return errorResponse('This endpoint has moved to /api/accounts/:id/verify-ghana-card', 410);
};
