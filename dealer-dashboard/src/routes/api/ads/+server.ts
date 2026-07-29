import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, getR2, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

// Self-healing: on a fresh D1 the ads migration may not have run yet — a
// carousels-worth of 500s is exactly what a client demo must never show.
async function ensureAdsTable(db: ReturnType<typeof getDb>) {
  try {
    await db.prepare(`CREATE TABLE IF NOT EXISTS ads (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      description TEXT NOT NULL DEFAULT '',
      image_url TEXT,
      link_url TEXT,
      is_active INTEGER NOT NULL DEFAULT 1,
      sort_order INTEGER NOT NULL DEFAULT 0,
      created_at INTEGER NOT NULL DEFAULT (unixepoch()),
      updated_at INTEGER NOT NULL DEFAULT (unixepoch())
    )`).run();
    await db.prepare('CREATE INDEX IF NOT EXISTS idx_ads_active_sort ON ads(is_active, sort_order)').run();
  } catch { /* table exists / read-only replica — queries below will surface it */ }
}

type AdImage = { bytes: Uint8Array; mimeType: string; extension: string };

/**
 * Decode a browser FileReader data URL into uploadable bytes. Mirrors the KYC
 * photo decoder but lives here so the ads route stays self-contained. Throws on
 * malformed input so the caller can surface a clean 400.
 */
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

/** External (pasted) URLs are http(s); uploaded assets are stored as R2 keys. */
function isHttpUrl(value: string | null | undefined): boolean {
  return !!value && /^https?:\/\//i.test(value);
}

export const GET: RequestHandler = async ({ url, locals, platform }) => {
  const db = getDb({ platform });
  await ensureAdsTable(db);
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
        description: (r.description as string | null) || '',
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
      description: (r.description as string | null) || '',
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

  const { title, description, imageUrl, imageData, linkUrl, isActive, order } = await request.json();
  if (!title || !title.trim()) return errorResponse('Title is required', 400);

  const db = getDb({ platform });
  await ensureAdsTable(db);
  const adId = `AD-${uuidv4().slice(0, 8).toUpperCase()}`;
  const now = Math.floor(Date.now() / 1000);

  // An uploaded file (base64 data URL) wins over a pasted URL. Uploaded bytes go
  // to R2 and we store the R2 key (a non-http string) in image_url; customer
  // devices resolve that to /api/ads/{id}/image. A pasted external URL is kept
  // verbatim so the client can load it directly.
  let storedImageUrl: string | null = imageUrl || null;
  if (imageData) {
    try {
      const decoded = decodeAdImage(imageData);
      if (decoded) {
        const key = `ads/${adId}.${decoded.extension}`;
        await getR2({ platform }).put(key, decoded.bytes, { httpMetadata: { contentType: decoded.mimeType } });
        storedImageUrl = key;
      }
    } catch (e) {
      return errorResponse(e instanceof Error ? e.message : 'Invalid image upload', 400);
    }
  }

  await db.prepare(`
    INSERT INTO ads (id, title, description, image_url, link_url, is_active, sort_order, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).bind(
    adId,
    title.trim(),
    (description || '').trim(),
    storedImageUrl,
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
      imageUrl: storedImageUrl,
      linkUrl: linkUrl || null,
      isActive: isActive !== false ? 1 : 0,
      order: typeof order === 'number' ? order : 0,
      createdAt: new Date(now * 1000).toISOString(),
      updatedAt: new Date(now * 1000).toISOString()
    }
  }, { status: 201 });
};
