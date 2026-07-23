import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, readAgentApkMeta } from '$lib/api/server';

export const GET: RequestHandler = async ({ url, locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (!['SUPER_ADMIN', 'AGENCY_OWNER', 'BRANCH_ADMIN', 'AGENT'].includes(locals.dealer.role)) {
    return errorResponse('Insufficient permissions', 403);
  }

  const currentVersionCode = Number(url.searchParams.get('currentVersionCode') ?? 0);
  if (!Number.isSafeInteger(currentVersionCode) || currentVersionCode <= 0) {
    return errorResponse('A valid currentVersionCode is required', 400);
  }

  let meta;
  try {
    meta = await readAgentApkMeta({ platform });
  } catch {
    return errorResponse('Agent APK metadata is not published yet', 503);
  }

  const configuredMin = Number(platform?.env?.AGENT_APP_MIN_SUPPORTED_VERSION_CODE ?? 0);
  const minSupportedVersionCode = Number.isSafeInteger(configuredMin) && configuredMin > 0
    ? configuredMin
    : meta.versionCode;
  const available = meta.versionCode > currentVersionCode;

  return json({
    available,
    url: available ? meta.url : '',
    sha256Base64: available ? meta.sha256Base64 : '',
    signatureChecksumBase64: available ? (meta.signatureChecksumBase64 ?? '') : '',
    versionName: meta.versionName,
    versionCode: meta.versionCode,
    minSupportedVersionCode,
    serverTime: Date.now()
  });
};
