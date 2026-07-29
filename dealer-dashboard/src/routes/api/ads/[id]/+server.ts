import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, getR2, errorResponse } from '$lib/api/server';

type AdImage = { bytes: Uint8Array; mimeType: string; extension: string };

function decodeAdImage(value: unknown): AdImage | null {
  const input = typeof value === 'string' ? value.trim() : '';
  if (!input) return null;
  const match = input.match(/^data:image\/(jpeg|jpg|png|webp);base64,([A-Za-z0-9+/=\s]+)$/i);
  if (!match) throw new Error('Uploaded image must be a JPEG, PNG or WebP file');
  const mimeToken = match[1].toLowerCase();
  const payload = match[2].replace(/\s/g, '');
  if (!/^[A-Za-z0-9+/]*={0,2}$/.test(payload) || payload.length % 4 === 1) {
    throw new Error('Uploaded image is not valid Base64 data');
  }
  let binary: string;
  try {
    binary = atob(payload);
  } catch {
    throw new Error('Uploaded image is not valid Base64 data');
  }
  if (binary.length === 0 || binary.length > 5 * 1024 * 1024) {
    throw new Error('Uploaded image must be between 1 byte and 5MB');
  }
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  if (mimeToken === 'png') return { bytes, mimeType: 'image/png', extension: 'png' };
  if (mimeToken === 'webp') return { bytes, mimeType: 'image/webp', extension: 'webp' };
  return { bytes, mimeType: 'image/jpeg', extension: 'jpg' };
}

function isHttpUrl(value: string | null | undefined): boolean {
  return !!value && /^https?:\/\//i.test(value);
}

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
      description: (ad.description as string | null) || '',
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
  const existing = await db.prepare('SELECT id, image_url FROM ads WHERE id = ?')
    .bind(params.id)
    .first<{ id: string; image_url: string | null }>();
  if (!existing) return errorResponse('Ad not found', 404);

  const { title, description, imageUrl, imageData, linkUrl, isActive, order } = await request.json();
  if (!title || !title.trim()) return errorResponse('Title is required', 400);

  const now = Math.floor(Date.now() / 1000);
  const oldStored = existing.image_url;

  // imageUrl === undefined means "leave the image untouched" (the form omits it
  // when neither the URL nor the upload changed); null/'' clears it; a string
  // sets it. A fresh upload (imageData) always wins and writes a new R2 object.
  let storedImageUrl: string | null = imageUrl === undefined ? oldStored : (imageUrl || null);
  if (imageData) {
    try {
      const decoded = decodeAdImage(imageData);
      if (decoded) {
        const key = `ads/${params.id}.${decoded.extension}`;
        await getR2({ platform }).put(key, decoded.bytes, { httpMetadata: { contentType: decoded.mimeType } });
        storedImageUrl = key;
      }
    } catch (e) {
      return errorResponse(e instanceof Error ? e.message : 'Invalid image upload', 400);
    }
  }

  await db.prepare(`
    UPDATE ads
    SET title = ?, description = ?, image_url = ?, link_url = ?, is_active = ?, sort_order = ?, updated_at = ?
    WHERE id = ?
  `).bind(
    title.trim(),
    (description || '').trim(),
    storedImageUrl,
    linkUrl || null,
    isActive !== false ? 1 : 0,
    typeof order === 'number' ? order : 0,
    now,
    params.id
  ).run();

  // Best-effort: drop the previous uploaded object when it was replaced or the
  // image was cleared. External URLs are never deleted (we don't own them).
  if (oldStored && !isHttpUrl(oldStored) && oldStored !== storedImageUrl) {
    try { await getR2({ platform }).delete(oldStored); } catch { /* non-fatal */ }
  }

  const updated = await db.prepare(`
    SELECT id, title, description, image_url, link_url, is_active, sort_order, created_at, updated_at
    FROM ads WHERE id = ?
  `).bind(params.id).first();

  // The row was just written in this request, so a null here means the ad was
  // deleted concurrently — report that instead of dereferencing null.
  if (!updated) return errorResponse('Ad not found', 404);

  return json({
    success: true,
    ad: {
      id: updated.id,
      title: updated.title,
      description: (updated.description as string | null) || '',
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
  const existing = await db.prepare('SELECT id, image_url FROM ads WHERE id = ?')
    .bind(params.id)
    .first<{ id: string; image_url: string | null }>();
  if (!existing) return errorResponse('Ad not found', 404);

  await db.prepare('DELETE FROM ads WHERE id = ?').bind(params.id).run();

  // Reclaim storage for an uploaded asset. External URLs are left alone.
  const oldStored = existing.image_url;
  if (oldStored && !isHttpUrl(oldStored)) {
    try { await getR2({ platform }).delete(oldStored); } catch { /* non-fatal */ }
  }

  return json({ success: true, id: params.id });
};
