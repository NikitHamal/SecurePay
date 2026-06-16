import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import {
  getDb,
  errorResponse,
  generateToken,
  generateActivationCode,
  buildQrPayload,
  readApkMeta
} from '$lib/api/server';

const TOKEN_TTL_SEC = 24 * 60 * 60;
const MAX_CODE_RETRIES = 10;

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const body = await request.json();
  const { imei, wifiSsid, wifiPassword } = body;

  if (!imei || !/^\d{15}$/.test(String(imei))) {
    return errorResponse('A valid 15-digit IMEI is required', 400);
  }

  const db = getDb({ platform });

  const device = await db.prepare(
    'SELECT id, imei, model, status FROM devices WHERE imei = ? AND dealer_id = ?'
  ).bind(String(imei), locals.dealer.id).first();

  if (!device) {
    return errorResponse('Device not found in your inventory', 404);
  }

  const account = await db.prepare(
    'SELECT id, customer_name FROM accounts WHERE device_id = ? AND dealer_id = ?'
  ).bind(device.id as string, locals.dealer.id).first();

  if (!account) {
    return errorResponse(
      'This device has not been enrolled yet. Enroll the customer before provisioning.',
      409
    );
  }

  let apkMeta;
  try {
    apkMeta = await readApkMeta({ platform });
  } catch (e) {
    return errorResponse(
      'The TB User APK has not been published yet. The dealer must publish the APK (via CI) before generating a provisioning QR.',
      503
    );
  }

  const nowSec = Math.floor(Date.now() / 1000);
  const expiresAt = nowSec + TOKEN_TTL_SEC;

  await db.prepare(
    "UPDATE provisioning_tokens SET status = 'revoked' WHERE account_id = ? AND status IN ('pending','provisioned')"
  ).bind(account.id as string).run();

  const qrPayload = buildQrPayload({ apk: apkMeta, wifiSsid, wifiPassword });

  let tokenId = generateToken(16);
  let activationCode = generateActivationCode();
  let inserted = false;

  for (let attempt = 0; attempt < MAX_CODE_RETRIES; attempt++) {
    tokenId = generateToken(16);
    activationCode = generateActivationCode();
    const res = await db.prepare(
      `INSERT INTO provisioning_tokens
        (id, account_id, device_id, dealer_id, activation_code, status, wifi_ssid, wifi_password, created_at, expires_at)
       VALUES (?, ?, ?, ?, ?, 'pending', ?, ?, ?, ?)`
    ).bind(
      tokenId,
      account.id as string,
      device.id as string,
      locals.dealer.id,
      activationCode,
      wifiSsid ? String(wifiSsid) : null,
      wifiPassword ? String(wifiPassword) : null,
      nowSec,
      expiresAt
    ).run();

    if (res.success) {
      inserted = true;
      break;
    }
  }

  if (!inserted) {
    return errorResponse('Failed to generate a unique activation code. Please try again.', 500);
  }

  return json({
    token: tokenId,
    activationCode,
    qrPayload,
    expiresAt: expiresAt * 1000,
    account: {
      id: account.id,
      customerName: account.customer_name
    },
    device: {
      imei: device.imei,
      model: device.model
    }
  }, { status: 201 });
};
