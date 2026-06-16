import type { D1Database } from '@cloudflare/workers-types';
import type { R2Bucket } from '@cloudflare/workers-types';
import type { Status } from '$lib/types';

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

const DEVICE_ADMIN_COMPONENT = 'com.touchbase.user/com.touchbase.user.admin.SecurePayDeviceAdminReceiver';
const DEVICE_ADMIN_PACKAGE = 'com.touchbase.user';
const DEVICE_ADMIN_LABEL = 'TB User';

export interface ApkMeta {
  url: string;
  sha256Base64: string;
  signatureChecksumBase64: string;
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
  const text = await obj.text();
  return JSON.parse(text) as ApkMeta;
}

export interface QrPayloadInput {
  apk: ApkMeta;
  wifiSsid?: string | null;
  wifiPassword?: string | null;
}

export function buildQrPayload({ apk, wifiSsid, wifiPassword }: QrPayloadInput): string {
  const payload: Record<string, string> = {
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME': DEVICE_ADMIN_COMPONENT,
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME': DEVICE_ADMIN_PACKAGE,
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION': apk.url,
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM': apk.sha256Base64,
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM': apk.signatureChecksumBase64,
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_LABEL': DEVICE_ADMIN_LABEL
  };
  if (apk.versionCode > 0) {
    payload['android.app.extra.PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE'] = String(apk.versionCode);
  }
  if (wifiSsid) {
    payload['android.app.extra.PROVISIONING_WIFI_SSID'] = JSON.stringify(wifiSsid);
  }
  if (wifiPassword) {
    payload['android.app.extra.PROVISIONING_WIFI_PASSWORD'] = JSON.stringify(wifiPassword);
  }
  return JSON.stringify(payload);
}
