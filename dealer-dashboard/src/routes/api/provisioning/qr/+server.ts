import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import {
  getDb,
  errorResponse,
  generateToken,
  generateActivationCode,
  findUnusedActivationCode,
  buildQrPayload,
  readApkMeta,
  getDealerSecurityPolicy
} from '$lib/api/server';

const TOKEN_TTL_SEC = 24 * 60 * 60;

function isValidWpaPassword(password: string): boolean {
  return (password.length >= 8 && password.length <= 63) || /^[0-9a-fA-F]{64}$/.test(password);
}

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const body = await request.json();
  const imei = String(body.imei ?? '').trim();
  const wifiSsid = typeof body.wifiSsid === 'string' ? body.wifiSsid.trim() : '';
  const wifiPassword = typeof body.wifiPassword === 'string' ? body.wifiPassword : '';

  if (!/^\d{15}$/.test(imei)) {
    return errorResponse('A valid 15-digit IMEI is required', 400);
  }
  if (wifiPassword && !wifiSsid) {
    return errorResponse('Wi-Fi SSID is required when a password is supplied', 400);
  }
  if (wifiSsid && new TextEncoder().encode(wifiSsid).length > 32) {
    return errorResponse('Wi-Fi SSID must be 32 bytes or fewer', 400);
  }
  if (wifiPassword && !isValidWpaPassword(wifiPassword)) {
    return errorResponse('Wi-Fi password must be 8–63 characters or a 64-digit hexadecimal PSK', 400);
  }

  const db = getDb({ platform });

  const device = await db.prepare(
    'SELECT id, imei, model, status FROM devices WHERE imei = ? AND dealer_id = ?'
  ).bind(imei, locals.dealer.id).first();

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
  } catch (error) {
    console.error('Unable to read published customer APK metadata', error);
    return errorResponse(
      'The TB User APK is not published correctly. Publish a fresh signed APK before generating a QR.',
      503
    );
  }

  const nowSec = Math.floor(Date.now() / 1000);
  const expiresAt = nowSec + TOKEN_TTL_SEC;

  await db.prepare(
    "UPDATE provisioning_tokens SET status = 'revoked' WHERE account_id = ? AND status IN ('pending','provisioned')"
  ).bind(account.id as string).run();

  let tokenId = '';
  let activationCode = '';

  try {
    activationCode = await findUnusedActivationCode(db, generateActivationCode);
    tokenId = generateToken(32);

    const insert = await db.prepare(
      `INSERT INTO provisioning_tokens
        (id, account_id, device_id, dealer_id, activation_code, status, wifi_ssid, wifi_password, created_at, expires_at)
       VALUES (?, ?, ?, ?, ?, 'pending', ?, NULL, ?, ?)`
    ).bind(
      tokenId,
      account.id as string,
      device.id as string,
      locals.dealer.id,
      activationCode,
      wifiSsid || null,
      nowSec,
      expiresAt
    ).run();

    if (!insert.success) {
      throw new Error('Provisioning token insert returned success=false');
    }
  } catch (error) {
    console.error('Failed to provision token', error);
    return errorResponse('Failed to generate a unique activation code. Please try again.', 500);
  }

  let qrPayload: string;
  let securityPolicy;
  try {
    securityPolicy = await getDealerSecurityPolicy({ platform }, locals.dealer.id);
    qrPayload = buildQrPayload({
      apk: apkMeta,
      wifiSsid: wifiSsid || null,
      wifiPassword: wifiPassword || null,
      provisioningToken: tokenId,
      activationCode,
      expectedImei: imei,
      accountId: account.id as string,
      deviceId: device.id as string,
      dealerId: locals.dealer.id,
      securityPolicy
    });
  } catch (error) {
    await db.prepare("UPDATE provisioning_tokens SET status = 'revoked' WHERE id = ?")
      .bind(tokenId)
      .run();
    console.error('Failed to build provisioning QR payload', error);
    return errorResponse('Failed to build the provisioning QR payload', 500);
  }

  return json({
    token: tokenId,
    activationCode,
    qrPayload,
    qrPayloadVersion: 5,
    expiresAt: expiresAt * 1000,
    apk: {
      versionName: apkMeta.versionName,
      versionCode: apkMeta.versionCode,
      updatedAt: apkMeta.updatedAt
    },
    securityPolicy: {
      frpEnabled: securityPolicy.frpEnabled,
      frpAccountCount: securityPolicy.frpAccountIds.length,
      version: securityPolicy.version
    },
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
