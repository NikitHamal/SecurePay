import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getAccountScopeFilter } from '$lib/auth';
import { getDb, errorResponse } from '$lib/api/server';

type AccountLocationRow = {
  id: string;
  is_stolen: number | null;
};

type LatestLocationRow = {
  latitude: number;
  longitude: number;
  accuracy: number | null;
  battery_level: number | null;
  timestamp: number;
};

/**
 * Dealer/agent endpoint for viewing the latest reported stolen-device location.
 *
 * Important behavior:
 * - 404 is now reserved for a genuinely missing/unauthorized account.
 * - A valid account with no uploaded location yet returns 200 with available=false.
 *   This prevents the dashboard/agent live-location screen from throwing noisy
 *   “Failed to load resource: 404” errors while the phone is still waiting for
 *   GPS/network permission or has not sent its first ping.
 */
export const GET: RequestHandler = async ({ params, locals, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const accountId = String(params.id ?? '').trim();
  if (!accountId) {
    return errorResponse('Account id is required', 400);
  }

  const db = getDb({ platform });
  const scope = getAccountScopeFilter(locals.dealer, 'a');

  const account = await db.prepare(`
    SELECT a.id, a.is_stolen
      FROM accounts a
     WHERE a.id = ? AND ${scope.where}
     LIMIT 1
  `).bind(accountId, ...scope.params).first<AccountLocationRow>();

  if (!account) {
    return errorResponse('Account not found', 404);
  }

  const row = await db.prepare(`
    SELECT latitude, longitude, accuracy, battery_level, timestamp
      FROM location_logs
     WHERE account_id = ?
     ORDER BY timestamp DESC, created_at DESC
     LIMIT 1
  `).bind(accountId).first<LatestLocationRow>();

  if (!row) {
    return json({
      available: false,
      accountId,
      isStolen: Number(account.is_stolen ?? 0) === 1,
      message: Number(account.is_stolen ?? 0) === 1
        ? 'No location ping has been received yet. Keep the customer phone online and wait for the tracking service to upload its first GPS fix.'
        : 'No location data is available because this account is not currently flagged as stolen.',
      serverTime: Date.now()
    });
  }

  return json({
    available: true,
    accountId,
    isStolen: Number(account.is_stolen ?? 0) === 1,
    latitude: row.latitude,
    longitude: row.longitude,
    lat: row.latitude,
    lng: row.longitude,
    accuracy: row.accuracy,
    battery: row.battery_level,
    batteryLevel: row.battery_level,
    timestamp: row.timestamp,
    serverTime: Date.now()
  });
};
