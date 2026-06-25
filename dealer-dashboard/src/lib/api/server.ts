import type { D1Database } from '@cloudflare/workers-types';
import type { R2Bucket } from '@cloudflare/workers-types';
import type { Status } from '$lib/types';

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

const RELEASE_HORIZON_MS = 10 * 365 * DAY_MS;

export function releaseHorizon(now = Date.now()): number {
  return now + RELEASE_HORIZON_MS;
}

export interface DeviceSecurityPolicyPayload {
  version: number;
  frpEnabled: boolean;
  frpAccountIds: string[];
  blockFactoryReset: boolean;
  blockSafeBoot: boolean;
  blockDeveloperOptions: boolean;
  blockUnknownSources: boolean;
  blockAccountModification: boolean;
}

export function parseFrpAccountIds(raw: unknown): string[] {
  if (Array.isArray(raw)) {
    return raw.map((value) => String(value).trim())
      .filter((value) => /^[0-9]{6,32}$/.test(value))
      .filter((value, index, values) => values.indexOf(value) === index);
  }
  const text = String(raw ?? '').trim();
  if (!text) return [];
  try {
    const parsed = JSON.parse(text) as unknown;
    if (Array.isArray(parsed)) return parseFrpAccountIds(parsed);
  } catch {
    // Accept comma/newline separated IDs as a convenient ops format.
  }
  return text.split(/[\s,]+/)
    .map((value) => value.trim())
    .filter((value) => /^[0-9]{6,32}$/.test(value))
    .filter((value, index, values) => values.indexOf(value) === index);
}

export async function getDealerSecurityPolicy(event: { platform?: App.Platform | null }, dealerId: string): Promise<DeviceSecurityPolicyPayload> {
  const db = getDb(event);
  const row = await db.prepare('SELECT frp_account_ids, security_policy_updated_at FROM dealers WHERE id = ?')
    .bind(dealerId)
    .first<{ frp_account_ids?: string | null; security_policy_updated_at?: number | null }>();
  const fromDb = parseFrpAccountIds(row?.frp_account_ids);
  const fromEnv = parseFrpAccountIds(event.platform?.env?.FRP_ACCOUNT_IDS ?? '');
  const frpAccountIds = fromDb.length > 0 ? fromDb : fromEnv;
  const version = Number(row?.security_policy_updated_at ?? 0) * 1000 || Date.now();
  return {
    version,
    frpEnabled: frpAccountIds.length > 0,
    frpAccountIds,
    blockFactoryReset: true,
    blockSafeBoot: true,
    blockDeveloperOptions: true,
    blockUnknownSources: true,
    blockAccountModification: true
  };
}

export function generateDeviceApiSecret(): string {
  return generateToken(32);
}


export function releaseApproved(row: Record<string, unknown>): boolean {
  return Number(row.release_approved ?? 0) === 1 || Number(row.amount_paid ?? 0) >= Number(row.total_loan_amount ?? 1);
}

export function releaseFields(row: Record<string, unknown>): {
  releaseApproved: boolean;
  releaseApprovedAt: number | null;
  releasedAt: number | null;
} {
  const approvedAt = row.release_approved_at == null ? null : Number(row.release_approved_at) * 1000;
  const releasedAt = row.released_at == null ? null : Number(row.released_at) * 1000;
  return {
    releaseApproved: releaseApproved(row),
    releaseApprovedAt: approvedAt,
    releasedAt
  };
}

const DEVICE_ADMIN_COMPONENT = 'com.touchbase.securepay.client/com.touchbase.user.admin.SecurePayDeviceAdminReceiver';

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
  const CODE_DIGITS = 6;
  const RANGE = 1_000_000;
  const MAX_VALID = 0x100000000 - (0x100000000 % RANGE);
  const bytes = new Uint8Array(4);
  let n = MAX_VALID;
  while (n >= MAX_VALID) {
    crypto.getRandomValues(bytes);
    const view = new DataView(bytes.buffer);
    n = view.getUint32(0, false);
  }
  return (n % RANGE).toString().padStart(CODE_DIGITS, '0');
}

export async function findUnusedActivationCode(
  db: D1Database,
  generate: () => string,
  maxRetries = 50
): Promise<string> {
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    const code = generate();
    const existing = await db.prepare(
      'SELECT 1 FROM provisioning_tokens WHERE activation_code = ? LIMIT 1'
    ).bind(code).first();
    if (!existing) return code;
  }
  throw new Error('Unable to allocate a unique activation code after retries');
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
  if (parsed.signatureChecksumBase64 && !/^[A-Za-z0-9_-]{43}$/.test(String(parsed.signatureChecksumBase64))) {
    throw new Error('Published APK manifest contains an invalid base64url signing-certificate checksum');
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
  securityPolicy?: DeviceSecurityPolicyPayload;
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
  dealerId,
  securityPolicy
}: QrPayloadInput): string {
  // Pin provisioning to the exact, versioned APK bytes and component.
  // Keep the QR payload close to Samsung's documented DO QR contract: component,
  // HTTPS APK URL, package SHA-256 checksum, optional Wi-Fi, and admin extras.
  // Avoid redundant/optional checksum fields in the QR because one stale value can
  // make Setup Wizard fail with a generic IT-team error after download.
  const payload: Record<string, JsonValue> = {
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME': DEVICE_ADMIN_COMPONENT,
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION': apk.url,
    'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM': apk.sha256Base64,
    'android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED': true,
    'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE': {
      schemaVersion: 1,
      provisioningToken,
      activationCode,
      expectedImei,
      accountId,
      deviceId,
      dealerId,
      frpAccountIdsCsv: securityPolicy?.frpAccountIds?.join(',') ?? '',
      securityPolicyVersion: securityPolicy?.version ?? 0
    }
  };
  const ssid = wifiSsid?.trim();
  if (ssid) {
    payload['android.app.extra.PROVISIONING_WIFI_SSID'] = ssid;
    payload['android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE'] = wifiPassword ? 'WPA' : 'NONE';
    payload['android.app.extra.PROVISIONING_WIFI_HIDDEN'] = false;
    if (wifiPassword) {
      payload['android.app.extra.PROVISIONING_WIFI_PASSWORD'] = wifiPassword;
    }
  }

  return JSON.stringify(payload);
}
