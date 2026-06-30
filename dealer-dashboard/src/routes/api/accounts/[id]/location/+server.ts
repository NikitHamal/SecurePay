import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const db = getDb({ platform });
  
  // Verify this account belongs to this dealer
  const account = await db.prepare(`
    SELECT id FROM accounts WHERE id = ? AND dealer_id = ?
  `).bind(params.id, locals.dealer.id).first();

  if (!account) {
    return errorResponse('Account not found', 404);
  }

  // Get the most recent location logs
  const logs = await db.prepare(`
    SELECT id, latitude, longitude, accuracy, battery_level as batteryLevel, timestamp
    FROM location_logs
    WHERE account_id = ?
    ORDER BY timestamp DESC
    LIMIT 50
  `).bind(params.id).all();

  return json(logs.results || []);
};
