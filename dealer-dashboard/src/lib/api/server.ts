import type { D1Database } from '@cloudflare/workers-types';
import type { R2Bucket } from '@cloudflare/workers-types';
import type { Status } from '$lib/types';

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

const DEVICE_ADMIN_COMPONENT = 'com.touchbase.user/com.touchbase.user.admin.SecurePayDeviceAdminReceiver';
const DEVICE_ADMIN_LABEL = 'TB User';

export interface ApkMeta {
  url: string;
  sha256Base64: string;
  signatureChecksumBase64?: string;
  versionName: string;
  versionCode: number;
  updatedAt: number;
}

export function computeStatus(nextPaymentDueEpochMillis: number, now = Date.now()): Status {
  if (now >= nextPaymentDueEpochMillis) return 'LOCKED';
  if (nextPaymentDueEpochMillis - now <= DAY_MS) return 'WARNING';
  return 'ACTIVE';
}

export function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}

export function errorResponse(message: string, status = 400): Response {
  return jsonResponse({ error: message }, status);
}

export function parseBody<T>(request: Request): Promise<T> {
  return request.json() as Promise<T>;
}

export function getDb(event: { platform?: App.Platform | null }): D1Database {
  if (!event.platform?.env?.DB) {
    throw new Error('D1 database not available');
  }
  return event.platform.env.DB;
}

export function getR2(event: { platform?: App.Platform | null }): R2Bucket {
  if (!event.platform?.env?.R2) {
    throw new Error('R2 bucket not available');
  }
  return event.platform.env.R2;
}

export function getJwtSecret(event: { platform?: App.Platform | null }): string {
  if (!event.platform?.env?.JWT_SECRET) {
    throw new Error('JWT_SECRET not configured');
  }
  return event.platform.env.JWT_SECRET;
}

export function generateToken(byteLength = 16): string {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, '0')).join('');
}

export function generateActivationCode(): string {
  const n = crypto.getRandomValues(new Uint32Array(1))[0];
  return (n % 1000000).toString().padStart(6, '0');
}

export async function readApkMeta(event: { platform?: App.Platform | null }): Promise<ApkMeta> {
  const r2 = getR2(event);
  const obj = await r2.get('latest.json');
  if (!obj) {
    throw new Error('APK manifest not published yet — run CI to publish the TB User APK to R2');
  }

  const parsed = JSON.parse(await obj.text()) as Partial<ApkMeta>;
  const url = String(parsed.url ?? '');
  const sha256Base64 = String(parsed.sha256Base64 ?? '');
  const versionName = String(parsed.versionName ?? '');
  const versionCode = Number(parsed.versionCode ?? 0);
  const updatedAt = Number(parsed.updatedAt ?? 0);

  let apkUrl: URL;
  try {
    apkUrl = new URL(url);
  } catch {
    throw new Error('Published APK manifest contains an invalid URL');
  }
  if (apkUrl.protocol !== 'https:') {
    throw new Error('Provisioning APK URL must use HTTPS');
  }
  if (!/^[A-Za-z0-9_-]{43}$/.test(sha256Base64)) {
    throw new Error('Published APK manifest contains an invalid base64url SHA-256 checksum');
  }
  if (!Number.isSafeInteger(versionCode) || versionCode <= 0 || !versionName || updatedAt <= 0) {
    throw new Error('Published APK manifest is incomplete');
  }

  return {
    url,
    sha256Base64,
    signatureChecksumBase64: parsed.signatureChecksumBase64
      ? String(parsed.signatureChecksumBase64)
      : undefined,
    versionName,
    versionCode,
    updatedAt
  };
}

type JsonPrimitive = string | number | boolean | null;
type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue };

export interface QrPayloadInput {
  apk: ApkMeta;
  wifiSsid?: string | null;
  wifiPassword?: string | null;
  provisioningToken: string;
  activationCode: string;
  expectedImei: string;
  accountId: string;
  deviceId: string;
  dealerId: string;
}

export function buildQrPayload({
  apk,
  wifiSsid,
  wifiPassword,
  provisioningToken,
  activationCode,
  expectedImei,
  accountId,
  deviceId,
  dealerId
}: QrPayloadInput): string {
  // Pin provisioning to the exact, versioned APK bytes. Do not also send the
  // deprecated package-name extra or a second signature checksum: one exact APK
  // checksum is deterministic and avoids conflicting validation paths.
  const payload: Record<string, JsonValue> = {
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME': DEVICE_ADMIN_COMPONENT,
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION': apk.url,
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM': apk.sha256Base64,
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_LABEL': DEVICE_ADMIN_LABEL,
    'android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED': true,
    'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE': {
      schemaVersion: 1,
      provisioningToken,
      activationCode,
      expectedImei,
      accountId,
      deviceId,
      dealerId
    }
  };

  const ssid = wifiSsid?.trim();
  if (ssid) {
    payload['android.app.extra.PROVISIONING_WIFI_SSID'] = ssid;
    payload['android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE'] = wifiPassword ? 'WPA' : 'NONE';
    if (wifiPassword) {
      payload['android.app.extra.PROVISIONING_WIFI_PASSWORD'] = wifiPassword;
    }
  }

  return JSON.stringify(payload);
}
