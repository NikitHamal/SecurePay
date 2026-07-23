import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const GET: RequestHandler = async ({ params, locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (locals.dealer.role !== 'SUPER_ADMIN') return errorResponse('Insufficient permissions', 403);

  const db = getDb({ platform });
  const ad = await db.prepare(`
    SELECT id, title, description, image_url, link_url, is_active, sort_order, created_at, updated_at
    FROM ads WHERE id = ?
  `).bind(params.id).first();

  if (!ad) return errorResponse('Ad not found', 404);

  return json({
    success: true,
    ad: {
      id: ad.id,
      title: ad.title,
      description: ad.description,
      imageUrl: ad.image_url,
      linkUrl: ad.link_url,
      isActive: ad.is_active === 1,
      order: Number(ad.sort_order),
      createdAt: ad.created_at ? new Date(Number(ad.created_at) * 1000).toISOString() : null,
      updatedAt: ad.updated_at ? new Date(Number(ad.updated_at) * 1000).toISOString() : null
    }
  });
};

export const PUT: RequestHandler = async ({ params, locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (locals.dealer.role !== 'SUPER_ADMIN') return errorResponse('Insufficient permissions', 403);

  const db = getDb({ platform });
  const existing = await db.prepare('SELECT id FROM ads WHERE id = ?').bind(params.id).first();
  if (!existing) return errorResponse('Ad not found', 404);

  const { title, description, imageUrl, linkUrl, isActive, order } = await request.json();
  if (!title || !title.trim()) return errorResponse('Title is required', 400);

  const now = Math.floor(Date.now() / 1000);

  await db.prepare(`
    UPDATE ads
    SET title = ?, description = ?, image_url = ?, link_url = ?, is_active = ?, sort_order = ?, updated_at = ?
    WHERE id = ?
  `).bind(
    title.trim(),
    (description || '').trim(),
    imageUrl || null,
    linkUrl || null,
    isActive !== false ? 1 : 0,
    typeof order === 'number' ? order : 0,
    now,
    params.id
  ).run();

  const updated = await db.prepare(`
    SELECT id, title, description, image_url, link_url, is_active, sort_order, created_at, updated_at
    FROM ads WHERE id = ?
  `).bind(params.id).first();

  return json({
    success: true,
    ad: {
      id: updated.id,
      title: updated.title,
      description: updated.description,
      imageUrl: updated.image_url,
      linkUrl: updated.link_url,
      isActive: updated.is_active === 1,
      order: Number(updated.sort_order),
      createdAt: updated.created_at ? new Date(Number(updated.created_at) * 1000).toISOString() : null,
      updatedAt: updated.updated_at ? new Date(Number(updated.updated_at) * 1000).toISOString() : null
    }
  });
};

export const DELETE: RequestHandler = async ({ params, locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (locals.dealer.role !== 'SUPER_ADMIN') return errorResponse('Insufficient permissions', 403);

  const db = getDb({ platform });
  const existing = await db.prepare('SELECT id FROM ads WHERE id = ?').bind(params.id).first();
  if (!existing) return errorResponse('Ad not found', 404);

  await db.prepare('DELETE FROM ads WHERE id = ?').bind(params.id).run();

  return json({ success: true, id: params.id });
};
