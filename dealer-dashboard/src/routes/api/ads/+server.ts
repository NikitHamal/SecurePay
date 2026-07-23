import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

export const GET: RequestHandler = async ({ url, locals, platform }) => {
  const db = getDb({ platform });
  const activeOnly = url.searchParams.get('active') === 'true';

  if (activeOnly) {
    const result = await db.prepare(`
      SELECT id, title, description, image_url, link_url, is_active, sort_order, created_at, updated_at
      FROM ads
      WHERE is_active = 1
      ORDER BY sort_order ASC, created_at DESC
    `).all();

    return json({
      success: true,
      ads: result.results.map(r => ({
        id: r.id,
        title: r.title,
        description: r.description,
        imageUrl: r.image_url,
        linkUrl: r.link_url,
        isActive: r.is_active === 1,
        order: Number(r.sort_order),
        createdAt: r.created_at ? new Date(Number(r.created_at) * 1000).toISOString() : null,
        updatedAt: r.updated_at ? new Date(Number(r.updated_at) * 1000).toISOString() : null
      }))
    });
  }

  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (locals.dealer.role !== 'SUPER_ADMIN') return errorResponse('Insufficient permissions', 403);

  const result = await db.prepare(`
    SELECT id, title, description, image_url, link_url, is_active, sort_order, created_at, updated_at
    FROM ads
    ORDER BY sort_order ASC, created_at DESC
  `).all();

  return json({
    success: true,
    ads: result.results.map(r => ({
      id: r.id,
      title: r.title,
      description: r.description,
      imageUrl: r.image_url,
      linkUrl: r.link_url,
      isActive: r.is_active === 1,
      order: Number(r.sort_order),
      createdAt: r.created_at ? new Date(Number(r.created_at) * 1000).toISOString() : null,
      updatedAt: r.updated_at ? new Date(Number(r.updated_at) * 1000).toISOString() : null
    }))
  });
};

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (locals.dealer.role !== 'SUPER_ADMIN') return errorResponse('Insufficient permissions', 403);

  const { title, description, imageUrl, linkUrl, isActive, order } = await request.json();
  if (!title || !title.trim()) return errorResponse('Title is required', 400);

  const db = getDb({ platform });
  const adId = `AD-${uuidv4().slice(0, 8).toUpperCase()}`;
  const now = Math.floor(Date.now() / 1000);

  await db.prepare(`
    INSERT INTO ads (id, title, description, image_url, link_url, is_active, sort_order, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).bind(
    adId,
    title.trim(),
    (description || '').trim(),
    imageUrl || null,
    linkUrl || null,
    isActive !== false ? 1 : 0,
    typeof order === 'number' ? order : 0,
    now,
    now
  ).run();

  return json({
    success: true,
    ad: {
      id: adId,
      title: title.trim(),
      description: (description || '').trim(),
      imageUrl: imageUrl || null,
      linkUrl: linkUrl || null,
      isActive: isActive !== false ? 1 : 0,
      order: typeof order === 'number' ? order : 0,
      createdAt: new Date(now * 1000).toISOString(),
      updatedAt: new Date(now * 1000).toISOString()
    }
  }, { status: 201 });
};
