import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });
  const result = await db.prepare(`
    SELECT id, type, title, message, is_read, related_entity_type, related_entity_id, created_at
    FROM notifications
    WHERE recipient_id = ?
    ORDER BY created_at DESC
    LIMIT 50
  `).bind(locals.dealer.id).all();

  return json(result.results.map(r => ({
    id: r.id,
    type: r.type,
    title: r.title,
    message: r.message,
    isRead: r.is_read === 1,
    relatedEntityType: r.related_entity_type,
    relatedEntityId: r.related_entity_id,
    createdAt: Number(r.created_at) * 1000
  })));
};

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const { ids } = await request.json();
  if (!Array.isArray(ids) || ids.length === 0) {
    return errorResponse('ids must be a non-empty array', 400);
  }

  const db = getDb({ platform });
  for (const id of ids) {
    await db.prepare('UPDATE notifications SET is_read = 1 WHERE id = ? AND recipient_id = ?')
      .bind(String(id), locals.dealer.id).run();
  }

  return json({ message: 'Marked as read' });
};
