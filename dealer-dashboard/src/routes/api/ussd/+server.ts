import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import type { D1Database } from '@cloudflare/workers-types';
import { getDb, getPaystackSecret } from '$lib/api/server';
import {
  generateReference,
  hasPaystackConfigured,
  initializeCharge,
  verifyTransaction
} from '$lib/paystack';
import { getCustomerEmail } from '$lib/paystack/email';

/**
 * JEST USSD endpoint.
 *
 * JEST USSD forwards mobile handset USSD input to this URL as a POST with a
 * JSON body: USERID, MSISDN, USERDATA, MSGTYPE, SESSIONID, NETWORK (see
 * JEST_USSD_API_Documentation). We respond with USERID, MSISDN (optional),
 * MSG (<= 120 chars) and MSGTYPE (true = keep session, false = end).
 *
 * Configure JEST to POST to: https://<dashboard>/api/ussd
 * Set JEST_USSD_USER_ID to the ID assigned by JEST to enforce the caller.
 */

const SESSION_TTL_MS = 5 * 60 * 1000;
const MAX_AMOUNT_GHS = 50_000;
const DEFAULT_SUPPORT = '053 799 5936';

interface UssdBody {
  USERID?: unknown;
  MSISDN?: unknown;
  USERDATA?: unknown;
  MSGTYPE?: unknown;
  NETWORK?: unknown;
  SESSIONID?: unknown;
}

interface SessionRow {
  session_id: string;
  msisdn: string;
  network: string | null;
  step: string;
  amount_pesewas: number | null;
  provider: string | null;
  paystack_ref: string | null;
  created_at: number;
  updated_at: number;
}

function ussdReply(userId: string, msisdn: string, msg: string, msgType: boolean) {
  return json({
    USERID: userId,
    MSISDN: msisdn,
    MSG: truncate(msg, 120),
    MSGTYPE: msgType
  });
}

function truncate(s: string, max: number): string {
  return s.length > max ? s.slice(0, max - 1) + '…' : s;
}

/** Normalize any Ghana phone variant (0..., +233..., 233...) to 233XXXXXXXXX. */
function normalizeMsisdn(raw: string): string {
  const digits = raw.replace(/\D/g, '');
  if (digits.startsWith('233')) return digits;
  if (digits.startsWith('0')) return '233' + digits.slice(1);
  return digits;
}

function toPaystackPhone(msisdn: string): string {
  return '+' + msisdn;
}

function networkToProvider(network: string): string | null {
  const n = network.trim().toUpperCase();
  if (n.includes('MTN')) return 'mtn';
  if (n.includes('VOD') || n.includes('TELECEL')) return 'vod';
  if (n.includes('AIRTEL') || n.includes('TIGO') || n.includes('TGO')) return 'tgo';
  return null;
}

function providerLabel(provider: string): string {
  if (provider === 'mtn') return 'MTN MoMo';
  if (provider === 'vod') return 'Vodafone Cash';
  return 'AirtelTigo MoMo';
}

function formatDate(ms: number): string {
  return new Date(ms).toLocaleDateString('en-GB', {
    timeZone: 'UTC',
    day: '2-digit',
    month: 'short'
  });
}

function ghs(pesewas: number): string {
  return (pesewas / 100).toLocaleString('en-GB', { maximumFractionDigits: 0 });
}

interface StatusInfo {
  label: string;
  due: number;
}

function evaluateStatus(account: Record<string, any>): StatusInfo {
  const due = Number(account.next_payment_due) || 0;
  if (Number(account.release_approved) === 1) return { label: 'PAID OFF', due };
  if (Number(account.locked_by_dealer) === 1) return { label: 'LOCKED', due };
  if (due > 0 && due <= Date.now()) return { label: 'OVERDUE', due };
  return { label: 'ACTIVE', due };
}

async function loadSession(db: D1Database, sessionId: string): Promise<SessionRow | null> {
  const row = await db.prepare('SELECT * FROM ussd_sessions WHERE session_id = ?')
    .bind(sessionId).first<SessionRow>();
  if (!row) return null;
  if (Date.now() - row.updated_at * 1000 > SESSION_TTL_MS) return null;
  return row;
}

async function saveSession(
  db: D1Database,
  session: { session_id: string; msisdn: string; network: string | null; step: string; amount_pesewas: number | null; provider: string | null; paystack_ref: string | null }
): Promise<void> {
  await db.prepare(`
    INSERT INTO ussd_sessions (session_id, msisdn, network, step, amount_pesewas, provider, paystack_ref, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, unixepoch(), unixepoch())
    ON CONFLICT(session_id) DO UPDATE SET
      step = excluded.step,
      amount_pesewas = excluded.amount_pesewas,
      provider = excluded.provider,
      paystack_ref = excluded.paystack_ref,
      updated_at = unixepoch()
  `).bind(
    session.session_id,
    session.msisdn,
    session.network,
    session.step,
    session.amount_pesewas,
    session.provider,
    session.paystack_ref
  ).run();
}

async function findAccount(db: D1Database, msisdn: string): Promise<Record<string, any> | null> {
  const last9 = msisdn.slice(-9);
  const variants = [msisdn, `0${last9}`, `+${msisdn}`];
  const placeholders = variants.map(() => '?').join(', ');
  const exact = await db.prepare(
    `SELECT * FROM accounts WHERE phone_number IN (${placeholders}) LIMIT 1`
  ).bind(...variants).first<Record<string, any>>();
  if (exact) return exact;

  const fuzzy = await db.prepare(
    `SELECT * FROM accounts WHERE replace(replace(phone_number, '+', ''), ' ', '') LIKE '%' || ? LIMIT 1`
  ).bind(last9).first<Record<string, any>>();
  return fuzzy ?? null;
}

export const POST: RequestHandler = async ({ request, platform }) => {
  let body: UssdBody;
  try {
    body = await request.json();
  } catch {
    return ussdReply('', '', 'Service error. Please try again.', false);
  }

  const userId = String(body.USERID ?? '').trim();
  const msisdnRaw = String(body.MSISDN ?? '').trim();
  const userData = String(body.USERDATA ?? '').trim();
  const network = String(body.NETWORK ?? '').trim();
  const sessionId = String(body.SESSIONID ?? '').trim();

  let db: D1Database | null = null;
  try {
    db = getDb({ platform });
  } catch {
    return ussdReply(userId, msisdnRaw, 'Service error. Please try again.', false);
  }

  const reply = await runUssd(db, platform, {
    userId, msisdnRaw, userData, network, sessionId,
    msgTypeFirst: body.MSGTYPE === true || body.MSGTYPE === 1 || body.MSGTYPE === '1' || body.MSGTYPE === 'true' || body.MSGTYPE === 'TRUE'
  }).catch(() => ussdReply(userId, msisdnRaw, 'Service error. Please try again.', false));

  // Record the exchange for diagnostics (JEST gateway errors like 506).
  // Must be awaited — in Workers, un-awaited work is killed when the request returns.
  const respBody = await (reply as Response).clone().json().catch(() => null) as {
    MSG?: unknown; MSGTYPE?: unknown;
  } | null;
  await db.prepare(`
    INSERT INTO ussd_logs (session_id, msisdn, user_data, network, msg_type, response_msg, response_msg_type, error, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, unixepoch())
  `).bind(
    sessionId.slice(0, 128) || null,
    (msisdnRaw || '').slice(0, 32) || null,
    (userData || '').slice(0, 120) || null,
    (network || '').slice(0, 32) || null,
    body.MSGTYPE === true || body.MSGTYPE === 1 || body.MSGTYPE === '1' ? 1 : 0,
    respBody ? String(respBody.MSG ?? '').slice(0, 120) : null,
    respBody && respBody.MSGTYPE === true ? 1 : 0,
    null,
  ).run().catch((err) => console.error('[ussd] log write failed', err));

  return reply;
};

async function runUssd(
  db: D1Database,
  platform: App.Platform | null | undefined,
  input: { userId: string; msisdnRaw: string; userData: string; network: string; sessionId: string; msgTypeFirst: boolean }
): Promise<Response> {
  const { userId, msisdnRaw, userData, network, sessionId, msgTypeFirst } = input;

  if (!sessionId || !msisdnRaw) {
    return ussdReply(userId, msisdnRaw, 'Service error. Please try again.', false);
  }

  const configuredUserId =
    platform?.env?.JEST_USSD_USER_ID || (typeof process !== 'undefined' ? (process.env?.JEST_USSD_USER_ID || '') : '');
  if (configuredUserId && userId !== configuredUserId) {
    return ussdReply(userId, msisdnRaw, 'Unauthorized session. Please contact support.', false);
  }

  const msisdn = normalizeMsisdn(msisdnRaw);
  const account = await findAccount(db, msisdn);

  if (!account) {
    return ussdReply(userId, msisdn, `No TouchBase account found for this number. Support: ${supportLine(platform)}`, false);
  }

  let session = await loadSession(db, sessionId);
  if (!session || msgTypeFirst) {
    session = {
      session_id: sessionId,
      msisdn,
      network: network || null,
      step: 'main',
      amount_pesewas: null,
      provider: null,
      paystack_ref: null,
      created_at: Math.floor(Date.now() / 1000),
      updated_at: Math.floor(Date.now() / 1000)
    };
  }

  return handleStep(db, platform, session, userData, userId, msisdn, account);
}

function supportLine(platform: App.Platform | null | undefined): string {
  const line = platform?.env?.SUPPORT_PHONE || (typeof process !== 'undefined' ? (process.env?.SUPPORT_PHONE || '') : '');
  return (line || DEFAULT_SUPPORT).trim();
}

async function handleStep(
  db: D1Database,
  platform: App.Platform | null | undefined,
  session: SessionRow,
  userData: string,
  userId: string,
  msisdn: string,
  account: Record<string, any>
): Promise<Response> {
  const support = supportLine(platform);
  const step = session.step;

  // Main menu / any unrecognized step.
  if (step === 'main' || step === 'ended' || !['main', 'amount', 'provider', 'confirm'].includes(step)) {
    if (userData === '1') {
      await saveSession(db, { ...session, step: 'main' });
      const status = evaluateStatus(account);
      const msg =
        `Balance: GHS ${ghs(Number(account.amount_paid))}/${ghs(Number(account.total_loan_amount))}` +
        (status.due > 0 ? `\nDue: ${formatDate(status.due)}` : '') +
        `\nStatus: ${status.label}\n1. Menu\n0. Exit`;
      return ussdReply(userId, msisdn, msg, true);
    }
    if (userData === '2') {
      await saveSession(db, { ...session, step: 'amount' });
      return ussdReply(userId, msisdn, 'Enter amount to pay in GHS\n(whole cedis), e.g. 50:', true);
    }
    if (userData === '3') {
      return ussdReply(userId, msisdn, `Support: call or WhatsApp ${support}\nThank you.`, false);
    }
    if (userData === '0') {
      return ussdReply(userId, msisdn, 'Thank you for using TouchBase\nGoodbye!', false);
    }
    await saveSession(db, { ...session, step: 'main' });
    return ussdReply(userId, msisdn, 'TouchBase\n1. Balance & Status\n2. Pay via MoMo\n3. Support\n0. Exit', true);
  }

  if (step === 'amount') {
    return handleAmount(db, platform, session, userData, userId, msisdn, account);
  }

  if (step === 'provider') {
    const choice: Record<string, string> = { '1': 'mtn', '2': 'tgo', '3': 'vod' };
    const provider = choice[userData];
    if (!provider) {
      return ussdReply(userId, msisdn, 'Choose network:\n1. MTN\n2. AirtelTigo\n3. Vodafone', true);
    }
    return initiateCharge(db, platform, { ...session, provider }, userId, msisdn, account);
  }

  // confirm step — waiting for the user to approve the MoMo prompt on their phone.
  const reference = session.paystack_ref;
  if (!reference) {
    await saveSession(db, { ...session, step: 'main' });
    return ussdReply(userId, msisdn, 'TouchBase\n1. Balance & Status\n2. Pay via MoMo\n3. Support\n0. Exit', true);
  }
  if (userData === '1') {
    const secret = getPaystackSecret({ platform });
    let verified;
    try {
      verified = await verifyTransaction(reference, secret);
    } catch {
      verified = null;
    }
    if (verified?.status === 'success') {
      const fresh = await db.prepare('SELECT * FROM accounts WHERE id = ?')
        .bind(account.id).first<Record<string, any>>();
      const current = fresh ?? account;
      const status = evaluateStatus(current);
      const msg =
        `Payment received!\nPaid: GHS ${ghs(Number(current.amount_paid))}` +
        (status.label === 'PAID OFF'
          ? '\nYour phone is now unlocked\nThank you!'
          : `\nNext due: ${formatDate(status.due)}\n1. Menu\n0. Exit`);
      await saveSession(db, { ...session, step: 'ended' });
      return ussdReply(userId, msisdn, msg, true);
    }
    return ussdReply(userId, msisdn, 'Payment not confirmed yet\n1. Check again\n0. Exit', true);
  }
  if (userData === '0') {
    await saveSession(db, { ...session, step: 'main' });
    return ussdReply(userId, msisdn, 'Payment cancelled\n1. Balance & Status\n2. Pay via MoMo\n0. Exit', true);
  }
  return ussdReply(userId, msisdn, 'Reply 1 when you finish the prompt\non your phone, 0 to cancel', true);
}

async function handleAmount(
  db: D1Database,
  platform: App.Platform | null | undefined,
  session: SessionRow,
  userData: string,
  userId: string,
  msisdn: string,
  account: Record<string, any>
): Promise<Response> {
  const amountGhs = Number(userData);
  if (!Number.isFinite(amountGhs) || amountGhs <= 0) {
    return ussdReply(userId, msisdn, 'Invalid amount\nEnter whole cedis, e.g. 50:', true);
  }
  const amountPesewas = Math.round(amountGhs * 100);
  const remaining = Math.max(0, Number(account.total_loan_amount) - Number(account.amount_paid));
  if (amountPesewas > remaining) {
    return ussdReply(userId, msisdn, `Amount exceeds balance of GHS ${ghs(remaining)}\nEnter lower amount:`, true);
  }
  if (amountPesewas > MAX_AMOUNT_GHS * 100) {
    return ussdReply(userId, msisdn, `Max payment is GHS ${MAX_AMOUNT_GHS.toLocaleString()}\nEnter lower amount:`, true);
  }

  const provider = session.provider || networkToProvider(session.network || '');
  if (!provider) {
    await saveSession(db, { ...session, step: 'provider', amount_pesewas: amountPesewas });
    return ussdReply(userId, msisdn, 'Choose network:\n1. MTN\n2. AirtelTigo\n3. Vodafone', true);
  }
  return initiateCharge(db, platform, { ...session, provider, amount_pesewas: amountPesewas }, userId, msisdn, account);
}

async function initiateCharge(
  db: D1Database,
  platform: App.Platform | null | undefined,
  session: SessionRow,
  userId: string,
  msisdn: string,
  account: Record<string, any>
): Promise<Response> {
  const secret = getPaystackSecret({ platform });
  if (!hasPaystackConfigured(secret)) {
    return ussdReply(userId, msisdn, 'Mobile money is temporarily unavailable. Please try again later.', false);
  }
  const amountPesewas = session.amount_pesewas;
  const provider = session.provider;
  if (!amountPesewas || !provider) {
    return ussdReply(userId, msisdn, 'Session expired. Please start again.', false);
  }

  const phone = toPaystackPhone(msisdn);
  const email = getCustomerEmail(account, phone);
  const reference = generateReference('SPU');
  const nowSec = Math.floor(Date.now() / 1000);

  try {
    const result = await initializeCharge({
      amount: amountPesewas,
      email,
      currency: 'GHS',
      reference,
      channels: ['mobile_money'],
      metadata: { account_id: account.id, source: 'ussd' },
      mobile_money: { phone, provider: provider as 'mtn' | 'vod' | 'tgo' }
    }, secret);

    await db.prepare(`
      INSERT INTO paystack_transactions
        (id, reference, access_code, account_id, dealer_id, amount, currency, channel, provider,
         customer_email, customer_phone, status, gateway_response, metadata_json, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      result.id ?? null,
      reference,
      result.access_code ?? null,
      account.id,
      account.dealer_id,
      amountPesewas,
      'GHS',
      'mobile_money',
      provider,
      email,
      phone,
      result.status || 'pending',
      typeof result.message === 'string' ? result.message.slice(0, 500) : null,
      JSON.stringify({ account_id: account.id, source: 'ussd' }),
      nowSec,
      nowSec
    ).run();

    await saveSession(db, { ...session, step: 'confirm', paystack_ref: reference });
    return ussdReply(
      userId,
      msisdn,
      `Pay GHS ${ghs(amountPesewas)} via ${providerLabel(provider)}\nApprove the prompt on your phone\nReply 1 when done, 0 to cancel`,
      true
    );
  } catch (err: any) {
    console.error('[ussd] paystack charge failed', err);
    return ussdReply(userId, msisdn, 'Payment could not be started\nPlease try again later.', false);
  }
}
