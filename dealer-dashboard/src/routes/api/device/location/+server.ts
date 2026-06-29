import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

export const POST: RequestHandler = async ({ request, platform, locals }) => {
  if (!locals.hmacVerified) {
    return errorResponse('HMAC verification required', 401);
  }

  try {
    const body = await request.json();
    const { 
      accountId, 
      lat, 
      lng, 
      accuracy, 
      battery, 
      timestamp 
    } = body;

    if (!accountId || lat === undefined || lng === undefined) {
      return errorResponse('Missing location data', 400);
    }

    const db = getDb({ platform });
    
    // We use a batch insert in case the app sends multiple cached locations
    if (Array.isArray(body.logs)) {
      const stmt = db.prepare(`
        INSERT INTO location_logs (id, account_id, latitude, longitude, accuracy, battery_level, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `);
      
      const batch = body.logs.map((log: any) => 
        stmt.bind(
          uuidv4(),
          accountId,
          log.lat,
          log.lng,
          log.accuracy,
          log.battery,
          log.timestamp
        )
      );
      
      await db.batch(batch);
    } else {
      await db.prepare(`
        INSERT INTO location_logs (id, account_id, latitude, longitude, accuracy, battery_level, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `).bind(
        uuidv4(),
        accountId,
        lat,
        lng,
        accuracy,
        battery,
        timestamp || Math.floor(Date.now() / 1000)
      ).run();
    }

    return json({ success: true });
  } catch (e) {
    return errorResponse('Invalid request body', 400);
  }
};
