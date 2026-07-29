import type { RequestHandler } from './$types';
import { errorResponse, getDb, getR2 } from '$lib/api/server';

function contentTypeForExt(ext: string): string {
  if (ext === 'png') return 'image/png';
  if (ext === 'webp') return 'image/webp';
  return 'image/jpeg';
}

/**
 * Public, auth-free artwork endpoint. Customer devices load an uploaded ad
 * image here. It only ever streams objects a super-admin previously uploaded
 * (image_url stored as an R2 key, i.e. NOT an http(s) URL). Pasted external
 * URLs are loaded directly by the client and never pass through this route, so
 * serving here cannot be abused to proxy arbitrary remote content.
 */
export const GET: RequestHandler = async ({ params, platform }) => {
  const db = getDb({ platform });
  const ad = await db.prepare('SELECT image_url FROM ads WHERE id = ?')
    .bind(params.id)
    .first<{ image_url: string | null }>();

  if (!ad) return errorResponse('Ad not found', 404);

  const key = ad.image_url;
  // No image, or an external URL (the client loads those itself).
  if (!key || /^https?:\/\//i.test(key)) {
    return errorResponse('Ad has no uploaded image', 404);
  }

  try {
    const r2 = getR2({ platform });
    const obj = await r2.get(key);
    if (!obj) return errorResponse('Image not found in storage', 404);
    const ext = (key.split('.').pop() || 'jpg').toLowerCase();
    const bytes = await obj.arrayBuffer();
    return new Response(bytes, {
      headers: {
        'Content-Type': contentTypeForExt(ext),
        'Cache-Control': 'public, max-age=86400',
        'X-Content-Type-Options': 'nosniff'
      }
    });
  } catch (err) {
    return errorResponse((err as Error).message || 'Server error', 500);
  }
};
