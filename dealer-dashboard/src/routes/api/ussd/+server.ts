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
 * JSON body: USERID, MSISDN, USERDATA, MSGTYPE, SESSIONID, NETWORK.
 * We respond with USERID, MSISDN, MSG (<= 120 chars) and MSGTYPE (true = keep, false = release).
 */

const SESSION_TTL_MS = 5 * 60 * 1000;
const MAX_AMOUNT_GHS = 50_000;
const DEFAULT_SUPPORT = '053 799 5936';
const WELCOME_PROMPT = 'WELCOME TO TOUCHBASE PHONES\nEnter your account number';
const MAIN_MENU = 'TouchBase\n1. Balance & Status\n2. Pay via MoMo\n3. Support\n0. Exit';

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
  account_id: string | null;
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

/** Convert MSISDN (233XXXXXXXXX) to local 10-digit format (0XXXXXXXXX) expected by Paystack charge API. */
function toPaystackPhone(msisdn: string): string {
  if (msisdn.startsWith('233') && msisdn.length === 12) {
    return '0' + msisdn.slice(3);
  }
  return msisdn;
}

/** Detect Ghana mobile money provider from phone number prefix (024/054/055/059/025 -> mtn, 020/050/053 -> vod, 026/056/027/057 -> tgo). */
function phoneToProvider(phone: string): string | null {
  const digits = (phone || '').replace(/\D/g, '');
  const local = digits.startsWith('233') ? '0' + digits.slice(3) : digits;
  const prefix = local.slice(0, 3);
  if (['024', '054', '055', '059', '025'].includes(prefix)) return 'mtn';
  if (['020', '050', '053'].includes(prefix)) return 'vod';
  if (['026', '056', '027', '057'].includes(prefix)) return 'tgo';
  return null;
}

function providerLabel(provider: string): string {
  if (provider === 'mtn') return 'MTN MoMo';
  if (provider === 'vod') return 'Telecel Cash';
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
  session: { session_id: string; msisdn: string; network: string | null; step: string; amount_pesewas: number | null; provider: string | null; paystack_ref: string | null; account_id: string | null }
): Promise<void> {
  await db.prepare(`
    INSERT INTO ussd_sessions (session_id, msisdn, network, step, amount_pesewas, provider, paystack_ref, account_id, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, unixepoch(), unixepoch())
    ON CONFLICT(session_id) DO UPDATE SET
      step = excluded.step,
      amount_pesewas = excluded.amount_pesewas,
      provider = excluded.provider,
      paystack_ref = excluded.paystack_ref,
      account_id = excluded.account_id,
      updated_at = unixepoch()
  `).bind(
    session.session_id,
    session.msisdn,
    session.network,
    session.step,
    session.amount_pesewas,
    session.provider,
    session.paystack_ref,
    session.account_id
  ).run();
}

/** Look up an account by its customer-facing account number (e.g. 12894 or 0537995936). */
async function findAccountByNumber(db: D1Database, accountNumber: string): Promise<Record<string, any> | null> {
  const raw = (accountNumber || '').trim();
  if (!raw) return null;
  const variants = [...new Set([raw, raw.replace(/\s+/g, ''), raw.replace(/^0+/, '')])].filter(Boolean);
  if (variants.length === 0) return null;
  const placeholders = variants.map(() => '?').join(', ');
  return db.prepare(
    `SELECT * FROM accounts WHERE customer_account_number IN (${placeholders}) LIMIT 1`
  ).bind(...variants).first<Record<string, any>>();
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

  let capturedError: string | null = null;

  const reply = await runUssd(db, platform, {
    userId, msisdnRaw, userData, network, sessionId,
    msgTypeFirst: body.MSGTYPE === true || body.MSGTYPE === 1 || body.MSGTYPE === '1' || body.MSGTYPE === 'true' || body.MSGTYPE === 'TRUE'
  }).catch((err: unknown) => {
    const msg = err instanceof Error ? err.message : String(err);
    capturedError = msg;
    return ussdReply(userId, msisdnRaw, 'Payment could not be started\nPlease try again later.', false);
  });

  // Record the exchange for diagnostics.
  const respBody = await (reply as Response).clone().json().catch(() => null) as {
    MSG?: unknown; MSGTYPE?: unknown;
  } | null;

  const errString = capturedError as string | null;
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
    errString ? errString.slice(0, 250) : null,
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

  const loadedSession = await loadSession(db, sessionId);
  const isNewSession = !loadedSession || msgTypeFirst;

  if (isNewSession) {
    const session: SessionRow = {
      session_id: sessionId,
      msisdn,
      network: network || null,
      step: 'account',
      amount_pesewas: null,
      provider: null,
      paystack_ref: null,
      account_id: null,
      created_at: Math.floor(Date.now() / 1000),
      updated_at: Math.floor(Date.now() / 1000)
    };
    await saveSession(db, session);
    return ussdReply(userId, msisdn, WELCOME_PROMPT, true);
  }

  return handleStep(db, platform, loadedSession, userData, userId, msisdn, null);
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
  account: Record<string, any> | null
): Promise<Response> {
  const support = supportLine(platform);
  const step = session.step;

  // Screen one — account number entry after dialing *920*264#.
  if (step === 'account') {
    const found = await findAccountByNumber(db, userData);
    if (!found) {
      return ussdReply(userId, msisdn, 'Account number not found\nPlease check and try again', true);
    }
    await saveSession(db, { ...session, step: 'main', account_id: String(found.id) });
    const name = String(found.customer_name || 'Customer').trim();
    return ussdReply(userId, msisdn, `Welcome ${name}\n${MAIN_MENU}`, true);
  }

  // Every other step needs the account the session resolved to.
  if (!account && session.account_id) {
    account = await db.prepare('SELECT * FROM accounts WHERE id = ?')
      .bind(session.account_id).first<Record<string, any>>();
  }
  if (!account) {
    await saveSession(db, { ...session, step: 'account', account_id: null });
    return ussdReply(userId, msisdn, WELCOME_PROMPT, true);
  }

  const name = String(account.customer_name || 'Customer').trim();

  // Status screen — '1' returns to main menu, '0' exits, anything else re-shows.
  if (step === 'status') {
    if (userData === '1') {
      await saveSession(db, { ...session, step: 'main' });
      return ussdReply(userId, msisdn, `Welcome ${name}\n${MAIN_MENU}`, true);
    }
    if (userData === '0') {
      return ussdReply(userId, msisdn, 'Thank you for using TouchBase\nGoodbye!', false);
    }
    const status = evaluateStatus(account);
    const msg =
      `Balance: GHS ${ghs(Number(account.amount_paid))}/${ghs(Number(account.total_loan_amount))}` +
      (status.due > 0 ? `\nDue: ${formatDate(status.due)}` : '') +
      `\nStatus: ${status.label}\n1. Menu\n0. Exit`;
    return ussdReply(userId, msisdn, msg, true);
  }

  // Payment-received screen — '1' returns to main menu, '0' exits.
  if (step === 'ended') {
    if (userData === '1') {
      await saveSession(db, { ...session, step: 'main' });
      return ussdReply(userId, msisdn, `Welcome ${name}\n${MAIN_MENU}`, true);
    }
    if (userData === '0') {
      return ussdReply(userId, msisdn, 'Thank you for using TouchBase\nGoodbye!', false);
    }
    await saveSession(db, { ...session, step: 'main' });
    return ussdReply(userId, msisdn, `Welcome ${name}\n${MAIN_MENU}`, true);
  }

  // Main menu / any unrecognized step.
  if (step === 'main' || !['main', 'status', 'ended', 'amount', 'provider', 'momo_phone', 'voucher', 'confirm'].includes(step)) {
    if (userData === '1') {
      await saveSession(db, { ...session, step: 'status' });
      const status = evaluateStatus(account);
      const msg =
        `Balance: GHS ${ghs(Number(account.amount_paid))}/${ghs(Number(account.total_loan_amount))}` +
        (status.due > 0 ? `\nDue: ${formatDate(status.due)}` : '') +
        `\nStatus: ${status.label}\n1. Menu\n0. Exit`;
      return ussdReply(userId, msisdn, msg, true);
    }
    if (userData === '2') {
      await saveSession(db, { ...session, step: 'amount' });
      return ussdReply(userId, msisdn, 'Enter amount to pay in GHS:', true);
    }
    if (userData === '3') {
      return ussdReply(userId, msisdn, `Support: call or WhatsApp ${support}\nThank you.`, false);
    }
    if (userData === '0') {
      return ussdReply(userId, msisdn, 'Thank you for using TouchBase\nGoodbye!', false);
    }
    await saveSession(db, { ...session, step: 'main' });
    return ussdReply(userId, msisdn, `Welcome ${name}\n${MAIN_MENU}`, true);
  }

  if (step === 'amount') {
    return handleAmount(db, platform, session, userData, userId, msisdn, account);
  }

  if (step === 'provider') {
    const choice: Record<string, string> = { '1': 'mtn', '2': 'vod', '3': 'tgo' };
    const provider = choice[userData];
    if (!provider) {
      return ussdReply(userId, msisdn, 'Choose network:\n1. MTN MoMo\n2. Telecel Cash\n3. AirtelTigo', true);
    }

    if (provider === 'vod') {
      await saveSession(db, { ...session, step: 'voucher', provider: 'vod' });
      return ussdReply(userId, msisdn, 'Enter Telecel Voucher Code\n(Dial *110# on Telecel to generate):', true);
    }

    // For MTN or AirtelTigo, if caller msisdn does not match provider, prompt for phone number
    const msisdnProvider = phoneToProvider(msisdn);
    if (msisdnProvider !== provider) {
      await saveSession(db, { ...session, step: 'momo_phone', provider });
      const label = provider === 'mtn' ? 'MTN MoMo' : 'AirtelTigo';
      return ussdReply(userId, msisdn, `Enter ${label} phone number:`, true);
    }

    return initiateCharge(db, platform, { ...session, provider }, userId, msisdn, account, { phone: msisdn });
  }

  if (step === 'momo_phone') {
    const enteredPhone = normalizeMsisdn(userData);
    if (!/^233\d{9}$/.test(enteredPhone)) {
      const label = session.provider === 'mtn' ? 'MTN MoMo' : 'AirtelTigo';
      return ussdReply(userId, msisdn, `Invalid phone number\nEnter valid 10-digit ${label} number:`, true);
    }
    return initiateCharge(db, platform, session, userId, msisdn, account, {
      provider: session.provider || 'mtn',
      phone: enteredPhone
    });
  }

  if (step === 'voucher') {
    const voucherCode = userData.trim().replace(/\D/g, '');
    if (!voucherCode || voucherCode.length < 4) {
      return ussdReply(userId, msisdn, 'Invalid voucher code\nEnter Telecel Voucher Code (from *110#):', true);
    }
    const payPhone = session.paystack_ref || msisdn;
    return initiateCharge(db, platform, session, userId, msisdn, account, {
      provider: 'vod',
      phone: payPhone,
      voucherCode
    });
  }

  // confirm step — waiting for the user to approve the MoMo prompt on their phone.
  const reference = session.paystack_ref;
  if (!reference) {
    await saveSession(db, { ...session, step: 'main' });
    return ussdReply(userId, msisdn, `Welcome ${name}\n${MAIN_MENU}`, true);
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
    return ussdReply(userId, msisdn, 'Invalid amount\nEnter amount in GHS:', true);
  }
  const amountPesewas = Math.round(amountGhs * 100);
  const remaining = Math.max(0, Number(account.total_loan_amount) - Number(account.amount_paid));
  if (amountPesewas > remaining) {
    return ussdReply(userId, msisdn, `Amount exceeds balance of GHS ${ghs(remaining)}\nEnter lower amount:`, true);
  }
  if (amountPesewas > MAX_AMOUNT_GHS * 100) {
    return ussdReply(userId, msisdn, `Max payment is GHS ${MAX_AMOUNT_GHS.toLocaleString()}\nEnter lower amount:`, true);
  }

  // Always present the network menu after amount entry
  await saveSession(db, { ...session, step: 'provider', amount_pesewas: amountPesewas });
  return ussdReply(userId, msisdn, 'Choose network:\n1. MTN MoMo\n2. Telecel Cash\n3. AirtelTigo', true);
}

async function initiateCharge(
  db: D1Database,
  platform: App.Platform | null | undefined,
  session: SessionRow,
  userId: string,
  msisdn: string,
  account: Record<string, any>,
  opts?: { provider?: string; phone?: string; voucherCode?: string }
): Promise<Response> {
  const secret = getPaystackSecret({ platform });
  if (!hasPaystackConfigured(secret)) {
    return ussdReply(userId, msisdn, 'Mobile money is temporarily unavailable. Please try again later.', false);
  }
  const amountPesewas = session.amount_pesewas;
  const provider = opts?.provider || session.provider;
  if (!amountPesewas || !provider) {
    return ussdReply(userId, msisdn, 'Session expired. Please start again.', false);
  }

  const targetMsisdn = opts?.phone || msisdn;
  const phone = toPaystackPhone(targetMsisdn);
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
      mobile_money: {
        phone,
        provider: provider as 'mtn' | 'vod' | 'tgo',
        voucher_code: opts?.voucherCode || undefined
      }
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

    await saveSession(db, { ...session, step: 'confirm', paystack_ref: reference, provider });
    return ussdReply(
      userId,
      msisdn,
      `Pay GHS ${ghs(amountPesewas)} via ${providerLabel(provider)}\nApprove the prompt on your phone\nReply 1 when done, 0 to cancel`,
      true
    );
  } catch (err: any) {
    const errDetail = err?.body?.message || err?.message || String(err);
    console.error('[ussd] paystack charge failed', errDetail, err);
    throw new Error(`Paystack charge failed: ${errDetail}`);
  }
}
