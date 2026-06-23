import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const POST: RequestHandler = async ({ request, platform }) => {
  try {
    const { tag, message, level } = await request.json();
    if (!tag || !message || !level) {
      return errorResponse('Invalid log format', 400);
    }
    const db = getDb({ platform });
    await db.prepare(
      'INSERT INTO device_logs (tag, message, level) VALUES (?, ?, ?)'
    ).bind(tag, message, level).run();
    return json({ success: true });
  } catch (err: any) {
    return errorResponse(err.message || 'Server error', 500);
  }
};

export const GET: RequestHandler = async ({ platform }) => {
  try {
    const db = getDb({ platform });
    const { results } = await db.prepare(
      'SELECT id, tag, message, level, datetime(created_at, \'unixepoch\', \'localtime\') as time FROM device_logs ORDER BY id DESC LIMIT 200'
    ).all();
    return json(results);
  } catch (err: any) {
    return errorResponse(err.message || 'Server error', 500);
  }
};

export const DELETE: RequestHandler = async ({ platform }) => {
  try {
    const db = getDb({ platform });
    await db.prepare('DELETE FROM device_logs').run();
    return json({ success: true });
  } catch (err: any) {
    return errorResponse(err.message || 'Server error', 500);
  }
};

