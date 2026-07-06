import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ params, locals, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const db = getDb({ platform });
  
  // Get the latest location for the device associated with this account
  const row = await db.prepare(`
    SELECT l.latitude, l.longitude, l.accuracy, l.battery_level, l.timestamp
    FROM location_logs l
    JOIN accounts a ON l.account_id = a.id
    WHERE a.id = ? AND a.dealer_id = ?
    ORDER BY l.timestamp DESC
    LIMIT 1
  `).bind(params.id, locals.dealer.id).first();

  if (!row) {
    return json({ error: 'No location data available' }, { status: 404 });
  }

  return json({
    lat: row.latitude,
    lng: row.longitude,
    accuracy: row.accuracy,
    battery: row.battery_level,
    timestamp: row.timestamp
  });
};
