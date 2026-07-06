import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

interface RawLocationLog {
  lat?: unknown;
  lng?: unknown;
  latitude?: unknown;
  longitude?: unknown;
  accuracy?: unknown;
  battery?: unknown;
  batteryLevel?: unknown;
  battery_level?: unknown;
  timestamp?: unknown;
}

interface NormalizedLocationLog {
  latitude: number;
  longitude: number;
  accuracy: number | null;
  batteryLevel: number | null;
  timestamp: number;
}

const MAX_BATCH_SIZE = 100;

function toFiniteNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function normalizeLog(raw: RawLocationLog): NormalizedLocationLog | null {
  const latitude = toFiniteNumber(raw.lat ?? raw.latitude);
  const longitude = toFiniteNumber(raw.lng ?? raw.longitude);
  if (latitude === null || longitude === null) return null;
  if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null;

  const accuracy = toFiniteNumber(raw.accuracy);
  const battery = toFiniteNumber(raw.battery ?? raw.batteryLevel ?? raw.battery_level);
  const timestamp = toFiniteNumber(raw.timestamp) ?? Math.floor(Date.now() / 1000);

  return {
    latitude,
    longitude,
    accuracy: accuracy === null ? null : Math.max(0, accuracy),
    batteryLevel: battery === null ? null : Math.max(0, Math.min(100, Math.round(battery))),
    timestamp: Math.floor(timestamp)
  };
}

export const POST: RequestHandler = async ({ request, platform, locals }) => {
  if (!locals.hmacVerified) {
    return errorResponse('HMAC verification required', 401);
  }

  try {
    const body = await request.json();
    const accountId = String(body.accountId ?? '').trim();
    const imei = String(body.imei ?? '').trim();

    if (!accountId || !/^\d{15}$/.test(imei)) {
      return errorResponse('A valid accountId and 15-digit IMEI are required', 400);
    }

    const rawLogs: RawLocationLog[] = Array.isArray(body.logs) && body.logs.length > 0
      ? body.logs
      : [body];

    if (rawLogs.length > MAX_BATCH_SIZE) {
      return errorResponse(`Too many location logs; maximum batch size is ${MAX_BATCH_SIZE}`, 413);
    }

    const logs = rawLogs
      .map((log) => normalizeLog(log))
      .filter((log): log is NormalizedLocationLog => log !== null);

    if (logs.length === 0) {
      return errorResponse('Missing or invalid location data', 400);
    }

    const db = getDb({ platform });

    const account = await db.prepare(`
      SELECT a.id
        FROM accounts a
        JOIN devices d ON d.id = a.device_id
       WHERE a.id = ? AND d.imei = ?
    `).bind(accountId, imei).first<{ id: string }>();

    if (!account) {
      return errorResponse('Device account not found', 404);
    }

    const stmt = db.prepare(`
      INSERT INTO location_logs (id, account_id, latitude, longitude, accuracy, battery_level, timestamp)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `);

    await db.batch(logs.map((log) =>
      stmt.bind(
        uuidv4(),
        accountId,
        log.latitude,
        log.longitude,
        log.accuracy,
        log.batteryLevel,
        log.timestamp
      )
    ));

    await db.prepare('UPDATE accounts SET updated_at = ? WHERE id = ?')
      .bind(Math.floor(Date.now() / 1000), accountId)
      .run();

    const latest = logs[logs.length - 1];
    return json({
      success: true,
      stored: logs.length,
      latest: {
        lat: latest.latitude,
        lng: latest.longitude,
        accuracy: latest.accuracy,
        battery: latest.batteryLevel,
        timestamp: latest.timestamp
      },
      serverTime: Date.now()
    });
  } catch (e) {
    return errorResponse('Invalid request body', 400);
  }
};
