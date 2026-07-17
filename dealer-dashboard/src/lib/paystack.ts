/**
 * Paystack server-side client.
 *
 * Uses the secret key from env (PAYSTACK_SECRET_KEY). Never expose the secret
 * key to the browser. All amounts are passed in the SUBUNIT of the currency
 * (GHS pesewas) to match how SecurePay already stores money (e.g. GH₵ 10 = 1000).
 * Paystack expects kobo/pesewas for GHS.
 */

export type PaystackChannel = 'mobile_money' | 'card' | 'bank' | 'ussd' | 'qr' | 'bank_transfer';
export type PaystackMobileProvider = 'mtn' | 'vod' | 'tgo' | 'atl'; // MTN, Vodafone, Telecel/AirtelTigo

export interface PaystackChargeRequest {
  amount: number;           // pesewas (subunit)
  email: string;
  currency: 'GHS' | 'NGN';
  reference: string;
  channels?: PaystackChannel[];
  metadata?: Record<string, unknown>;
  mobile_money?: { phone: string; provider: PaystackMobileProvider };
}

export interface PaystackChargeResponse {
  status: boolean;
  message: string;
  data: {
    reference: string;
    status: 'pending' | 'send_otp' | 'otp_sent' | 'success' | 'failed' | 'abandoned' | 'reversed' | 'send_pin' | 'send_address' | 'send_birthday' | 'open_url' | 'pay_offline' | 'timeout';
    message: string;
    display_text?: string;
    otp?: string;
    access_code?: string;
    id?: number;
    amount?: number;
    currency?: string;
    transaction_date?: string;
    customer?: { email?: string; phone?: string; customer_code?: string };
    authorization?: {
      authorization_code?: string;
      channel?: string;
      bank?: string;
      brand?: string;
      country_code?: string;
      last4?: string;
      bin?: string;
      reusable?: boolean;
      signature?: string;
      account_name?: string;
    };
    fees?: number | null;
    gateway_response?: string;
    paidAt?: string | null;
    metadata?: unknown;
  };
}

export interface PaystackVerifyResponse {
  status: boolean;
  message: string;
  data: {
    id: number;
    status: 'success' | 'failed' | 'abandoned' | 'reversed';
    reference: string;
    amount: number;
    currency: string;
    channel?: string;
    gateway_response?: string;
    paid_at?: string | null;
    fees?: number | null;
    authorization?: Record<string, unknown> & {
      authorization_code?: string;
      channel?: string;
      bank?: string;
      brand?: string;
      country_code?: string;
    };
    customer?: { email?: string; phone?: string; customer_code?: string };
    metadata?: unknown;
  };
}

export interface PaystackWebhookEvent {
  event: string;
  data: Record<string, unknown>;
}

const PAYSTACK_BASE = 'https://api.paystack.co';

function getSecret(): string {
  if (typeof process !== 'undefined' && process.env?.PAYSTACK_SECRET_KEY) {
    return process.env.PAYSTACK_SECRET_KEY;
  }
  // Cloudflare Workers / SvelteKit platform.env
  // @ts-ignore
  const platformSecret = globalThis?.__PAYSTACK_SECRET_KEY__;
  if (platformSecret) return platformSecret;
  return '';
}

export function hasPaystackConfigured(secret?: string): boolean {
  const s = secret || getSecret();
  return typeof s === 'string' && s.length > 0;
}

async function paystackRequest<T>(
  path: string,
  opts: { method?: 'GET' | 'POST' | 'PUT'; body?: unknown; secret?: string } = {}
): Promise<T> {
  const secret = opts.secret || getSecret();
  if (!secret) throw new Error('Paystack secret key is not configured (set PAYSTACK_SECRET_KEY)');

  const res = await fetch(`${PAYSTACK_BASE}${path}`, {
    method: opts.method || 'GET',
    headers: {
      'Authorization': `Bearer ${secret}`,
      'Content-Type': 'application/json'
    },
    body: opts.body ? JSON.stringify(opts.body) : undefined
  });

  const text = await res.text();
  let json: any;
  try { json = JSON.parse(text); } catch { json = { status: false, message: text }; }

  if (!res.ok || json.status === false) {
    const msg = json?.message || `Paystack error ${res.status}`;
    const err = new Error(typeof msg === 'string' ? msg : JSON.stringify(msg));
    (err as any).status = res.status;
    (err as any).body = json;
    throw err;
  }
  return json as T;
}

/** Initiate a charge (mobile money / card). Returns the pending charge state. */
export async function initializeCharge(req: PaystackChargeRequest, secret?: string): Promise<PaystackChargeResponse['data']> {
  // Note: Ghana mobile_money uses the /charge endpoint (not /transaction/initialize)
  // so we can drive the OTP flow server-side without opening a checkout page.
  const body: Record<string, unknown> = {
    amount: req.amount,
    email: req.email,
    currency: req.currency,
    reference: req.reference,
    metadata: { ...(req.metadata || {}), source: 'securepay-dealer-console' }
  };
  if (req.channels && req.channels.length > 0) body.channels = req.channels;
  if (req.mobile_money) body.mobile_money = req.mobile_money;

  const res = await paystackRequest<PaystackChargeResponse>('/charge', { method: 'POST', body, secret });
  return res.data;
}

/** Submit OTP for pending_momo / pending_otp flow. */
export async function submitOtp(reference: string, otp: string, secret?: string): Promise<PaystackChargeResponse['data']> {
  const res = await paystackRequest<PaystackChargeResponse>('/charge/submit_otp', {
    method: 'POST',
    body: { reference, otp },
    secret
  });
  return res.data;
}

/** Requery / verify a transaction by reference. Used for polling and webhook idempotency. */
export async function verifyTransaction(reference: string, secret?: string): Promise<PaystackVerifyResponse['data']> {
  const res = await paystackRequest<PaystackVerifyResponse>(`/transaction/verify/${encodeURIComponent(reference)}`, { secret });
  return res.data;
}

/** List banks / providers (for Ghana). Useful for UI diagnostics; not strictly required. */
export async function listBanks(country: 'GH' | 'NG' = 'GH', secret?: string): Promise<Array<{ name: string; code: string; slug: string; currency: string; type: string }>> {
  const res = await paystackRequest<{ data: any[] }>(`/bank?country=${country}&perPage=100`, { secret });
  return res.data || [];
}

/**
 * Verify a Paystack webhook signature. Paystack signs POSTed events with an
 * HMAC-SHA256 of the raw request body using the secret key.
 */
export async function verifyWebhookSignatureAsync(rawBody: string, signatureHeader: string | null, secret?: string): Promise<boolean> {
  const key = secret || getSecret();
  if (!key || !signatureHeader) return false;
  try {
    const cryptoObj: Crypto | null = (typeof globalThis !== 'undefined' && (globalThis as any).crypto) ? (globalThis as any).crypto : null;
    if (cryptoObj?.subtle) {
      return await subtleVerify(cryptoObj, key, rawBody, signatureHeader);
    }
  } catch { /* fall through */ }
  return false;
}

/** Sync-compatible export — returns false when async verification is required. */
export function verifyWebhookSignature(rawBody: string, signatureHeader: string | null, secret?: string): boolean {
  // Async is required for Web Crypto API; webhook endpoint uses the async function.
  (async () => { /* placeholder */ })();
  const key = secret || getSecret();
  if (!key || !signatureHeader) return false;
  // Best-effort synchronous fallback is not possible in Workers/Node 18+ without a
  // bundled SHA-256 implementation, so return false and let the caller use the async version.
  return false;
}

async function subtleVerify(cryptoObj: Crypto, secret: string, body: string, expectedHex: string): Promise<boolean> {
  const enc = new TextEncoder();
  const keyData = enc.encode(secret);
  const key = await cryptoObj.subtle.importKey(
    'raw', keyData, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  const sig = await cryptoObj.subtle.sign('HMAC', key, enc.encode(body));
  const actual = buf2hex(new Uint8Array(sig));
  return timingSafeEqual(actual, expectedHex.replace(/^sha256=/, '').toLowerCase());
}

function buf2hex(buf: Uint8Array): string {
  let out = '';
  for (let i = 0; i < buf.length; i++) out += buf[i].toString(16).padStart(2, '0');
  return out;
}

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

/** Generate a unique transaction reference (URL/Paystack-safe). */
export function generateReference(prefix = 'SP'): string {
  const rand = Math.random().toString(36).slice(2, 10);
  const ts = Date.now().toString(36);
  return `${prefix}_${ts}_${rand}`.toUpperCase();
}
