import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, readApkMeta } from '$lib/api/server';
import { sendFcmTopic } from '$lib/api/fcm';

const UPDATE_TOPIC = 'tb-customer-updates';

export const POST: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (locals.dealer.role !== 'SUPER_ADMIN') return errorResponse('Only Super Admin can broadcast updates', 403);

  let meta;
  try {
    meta = await readApkMeta({ platform });
  } catch (error) {
    console.error('Cannot broadcast update without valid latest.json', error);
    return errorResponse('Published customer APK metadata is missing or invalid', 503);
  }

  const accepted = await sendFcmTopic('tb-customer-updates', {
    type: 'update',
    versionCode: String(meta.versionCode),
    versionName: meta.versionName
  }, platform?.env ?? {});

  if (!accepted) return errorResponse('Firebase Cloud Messaging is not configured or rejected the broadcast', 503);
  return json({ accepted: true, topic: UPDATE_TOPIC, versionCode: meta.versionCode, versionName: meta.versionName });
};
