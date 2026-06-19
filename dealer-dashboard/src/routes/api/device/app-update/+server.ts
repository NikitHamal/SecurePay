import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, readApkMeta } from '$lib/api/server';

export const GET: RequestHandler = async ({ url, platform, locals }) => {
  if (!locals.hmacVerified) return errorResponse('HMAC verification required', 401);

  const currentVersionCode = Number(url.searchParams.get('currentVersionCode') ?? 0);
  if (!Number.isSafeInteger(currentVersionCode) || currentVersionCode <= 0) {
    return errorResponse('A valid currentVersionCode is required', 400);
  }

  const meta = await readApkMeta({ platform });
  const available = meta.versionCode > currentVersionCode;
  return json({
    available,
    url: available ? meta.url : '',
    sha256Base64: available ? meta.sha256Base64 : '',
    versionName: meta.versionName,
    versionCode: meta.versionCode,
    minSupportedVersionCode: meta.versionCode,
    serverTime: Date.now()
  });
};
