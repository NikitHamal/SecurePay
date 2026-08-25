import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import type { D1Database } from '@cloudflare/workers-types';
import { getDb, getPaystackSecret } from '$lib/api/server';
import {
  generateReference,
  hasPaystackConfigured,
  initializeCharge,
  submitOtp,
  verifyTransaction
} from '$lib/paystack';
import { getCustomerEmail } from '$lib/paystack/email';
import { applyPayment } from '$lib/payments';
import { requeryPendingPayments } from '$lib/paystack-sync';

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

/**
 * Detect Ghana mobile money provider from phone number prefix.
 * MTN Ghana: 024, 054, 055, 059, 025, 053 -> 'mtn'
 * Telecel (Vodafone): 020, 050 -> 'vod'
 * AirtelTigo: 026, 056, 027, 057 -> 'atl'
 */
function phoneToProvider(phone: string): 'mtn' | 'vod' | 'atl' | null {
  const digits = (phone || '').replace(/\D/g, '');
  const local = digits.startsWith('233') ? '0' + digits.slice(3) : digits;
  const prefix = local.slice(0, 3);
  if (['024', '054', '055', '059', '025', '053'].includes(prefix)) return 'mtn';
  if (['020', '050'].includes(prefix)) return 'vod';
  if (['026', '056', '027', '057'].includes(prefix)) return 'atl';
  return null;
}

function providerLabel(provider: string): string {
  if (provider === 'mtn') return 'MTN MoMo';
  if (provider === 'vod') return 'Telecel Cash';
  if (provider === 'atl' || provider === 'tgo') return 'AirtelTigo MoMo';
  return 'Mobile Money';
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
    let userMsg = 'Payment could not be started\nPlease try again later.';
    if (msg.toLowerCase().includes('insufficient')) {
      userMsg = 'Payment failed: Insufficient funds in MoMo wallet.';
    } else if (msg.includes('Paystack charge failed:')) {
      const reason = msg.replace('Paystack charge failed:', '').trim();
      if (reason && reason !== 'Charge attempted' && reason !== 'undefined') {
        userMsg = `Payment failed: ${truncate(reason, 70)}`;
      }
    }
    return ussdReply(userId, msisdnRaw, userMsg, false);
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
    // Ghana MoMo 'pay_offline' charges complete AFTER this USSD session closes,
    // so requery Paystack for any approved-but-unapplied payments from this
    // number and credit them before the customer sees their balance.
    try {
      const psSecret = getPaystackSecret({ platform });
      if (psSecret) {
        const synced = await requeryPendingPayments(db, psSecret, { phone: toPaystackPhone(msisdn) });
        if (synced.some((r) => r.applied)) {
          console.log('[ussd] credited pending MoMo payment(s) for', msisdn, JSON.stringify(synced.filter((r) => r.applied)));
        }
      }
    } catch (e) {
      console.error('[ussd] startup payment sync failed', e);
    }

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
  if (step === 'main' || !['main', 'status', 'ended', 'amount', 'momo_phone', 'otp', 'confirm'].includes(step)) {
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

  if (step === 'momo_phone') {
    const enteredPhone = normalizeMsisdn(userData);
    if (!/^233\d{9}$/.test(enteredPhone)) {
      return ussdReply(userId, msisdn, 'Invalid phone number\nEnter valid 10-digit MoMo number:', true);
    }

    const detectedProvider = phoneToProvider(enteredPhone);
    if (!detectedProvider) {
      return ussdReply(userId, msisdn, 'Unsupported network\nEnter MTN, Telecel, or AirtelTigo number:', true);
    }

    const amountPesewas = session.amount_pesewas;
    if (!amountPesewas) {
      await saveSession(db, { ...session, step: 'amount' });
      return ussdReply(userId, msisdn, 'Enter amount to pay in GHS:', true);
    }

    return initiateCharge(db, platform, session, userId, msisdn, account, {
      amountPesewas,
      provider: detectedProvider,
      phone: enteredPhone
    });
  }

  if (step === 'otp') {
    if (userData === '0') {
      await saveSession(db, { ...session, step: 'main' });
      return ussdReply(userId, msisdn, `Welcome ${name}\n${MAIN_MENU}`, true);
    }
    const otp = userData.trim().replace(/\D/g, '');
    if (!otp) {
      return ussdReply(userId, msisdn, 'Invalid OTP\nEnter OTP sent to your phone\n(or 0 to cancel):', true);
    }
    const reference = session.paystack_ref;
    if (!reference) {
      await saveSession(db, { ...session, step: 'main' });
      return ussdReply(userId, msisdn, `Welcome ${name}\n${MAIN_MENU}`, true);
    }
    const secret = getPaystackSecret({ platform });
    try {
      const otpRes = await submitOtp(reference, otp, secret);
      if (otpRes.status === 'success') {
        // USSD is the fallback creditor if webhook hasn't fired yet — apply idempotently.
        try {
          const existing = await db.prepare('SELECT * FROM paystack_transactions WHERE reference = ?').bind(reference).first<Record<string, any>>();
          if (existing && !existing.payment_id) {
            const amt = Number((otpRes as any).amount || existing.amount || session.amount_pesewas || 0);
            if (amt > 0) {
              const { paymentId } = await applyPayment({ db, accountId: String(account.id), amount: amt, method: 'MOBILE_MONEY', reference: `paystack:${reference}`, recordedBy: 'ussd', env: platform?.env });
              await db.prepare(`UPDATE paystack_transactions SET status='success', payment_id=?, updated_at=unixepoch() WHERE reference=?`).bind(paymentId, reference).run();
            }
          } else if (!existing && session.amount_pesewas) {
            // No paystack_transactions row (should not happen) — still credit the account directly.
            await applyPayment({ db, accountId: String(account.id), amount: Number(session.amount_pesewas), method: 'MOBILE_MONEY', reference: `paystack:${reference}`, recordedBy: 'ussd', env: platform?.env });
            await db.prepare(`UPDATE paystack_transactions SET status='success', updated_at=unixepoch() WHERE reference=?`).bind(reference).run().catch(()=>{});
          }
        } catch (e) { console.error('[ussd] otp applyPayment failed', e); }
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
      if (otpRes.status === 'pay_offline' || otpRes.status === 'pending') {
        await saveSession(db, { ...session, step: 'confirm' });
        return ussdReply(userId, msisdn, 'OTP submitted. Please approve prompt on your phone\nReply 1 when done, 0 to cancel', true);
      }
      return ussdReply(userId, msisdn, `${otpRes.display_text || 'Payment processing'}\nReply 1 when done, 0 to cancel`, true);
    } catch (err: any) {
      const msg = err?.body?.message || err?.message || 'Invalid OTP';
      return ussdReply(userId, msisdn, `${msg}\nEnter OTP again (or 0 to cancel):`, true);
    }
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
      // Credit if webhook hasn't — idempotent.
      try {
        const existing = await db.prepare('SELECT * FROM paystack_transactions WHERE reference = ?').bind(reference).first<Record<string, any>>();
        if (existing && !existing.payment_id) {
          const amt = Number((verified as any).amount || existing.amount || session.amount_pesewas || 0);
          if (amt > 0) {
            const { paymentId } = await applyPayment({ db, accountId: String(account.id), amount: amt, method: 'MOBILE_MONEY', reference: `paystack:${reference}`, recordedBy: 'ussd', env: platform?.env });
            await db.prepare(`UPDATE paystack_transactions SET status='success', payment_id=?, updated_at=unixepoch() WHERE reference=?`).bind(paymentId, reference).run();
          }
        } else if (!existing && session.amount_pesewas) {
          await applyPayment({ db, accountId: String(account.id), amount: Number(session.amount_pesewas), method: 'MOBILE_MONEY', reference: `paystack:${reference}`, recordedBy: 'ussd', env: platform?.env });
        }
      } catch (e) { console.error('[ussd] confirm applyPayment failed', e); }
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

  // Auto-detect network from the caller's phone number
  const detected = phoneToProvider(msisdn);
  if (detected) {
    return initiateCharge(db, platform, session, userId, msisdn, account, {
      amountPesewas,
      provider: detected,
      phone: msisdn
    });
  }

  // Fallback if caller network cannot be determined from MSISDN
  await saveSession(db, { ...session, step: 'momo_phone', amount_pesewas: amountPesewas });
  return ussdReply(userId, msisdn, 'Enter MoMo phone number (10 digits):', true);
}

async function initiateCharge(
  db: D1Database,
  platform: App.Platform | null | undefined,
  session: SessionRow,
  userId: string,
  msisdn: string,
  account: Record<string, any>,
  opts: { amountPesewas: number; provider: 'mtn' | 'vod' | 'atl'; phone: string }
): Promise<Response> {
  const secret = getPaystackSecret({ platform });
  if (!hasPaystackConfigured(secret)) {
    return ussdReply(userId, msisdn, 'Mobile money is temporarily unavailable. Please try again later.', false);
  }

  const { amountPesewas, provider, phone: targetPhone } = opts;
  const payPhone = toPaystackPhone(targetPhone);
  const email = getCustomerEmail(account, payPhone);
  const reference = generateReference('SPU');
  const nowSec = Math.floor(Date.now() / 1000);

  try {
    const result = await initializeCharge({
      amount: amountPesewas,
      email,
      currency: 'GHS',
      reference,
      metadata: {
        account_id: account.id,
        account_number: account.customer_account_number,
        source: 'ussd'
      },
      mobile_money: {
        phone: payPhone,
        provider: provider
      }
    }, secret);

    await db.prepare(`
      INSERT INTO paystack_transactions
        (reference, access_code, account_id, dealer_id, amount, currency, channel, provider,
         customer_email, customer_phone, status, gateway_response, metadata_json, created_at, updated_at)
      VALUES (?, ?, ?, (SELECT dealer_id FROM accounts WHERE id = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      reference,
      result.access_code ?? null,
      account.id,
      account.id,
      amountPesewas,
      'GHS',
      'mobile_money',
      provider,
      email,
      payPhone,
      result.status || 'pending',
      typeof result.message === 'string' ? result.message.slice(0, 500) : (result.display_text || null),
      JSON.stringify({ account_id: account.id, source: 'ussd' }),
      nowSec,
      nowSec
    ).run().catch((e) => console.error('[ussd] paystack_transactions insert failed', e));

    if (result.status === 'send_otp') {
      await saveSession(db, {
        ...session,
        step: 'otp',
        amount_pesewas: amountPesewas,
        provider,
        paystack_ref: reference
      });
      return ussdReply(userId, msisdn, 'Enter OTP sent to your phone:', true);
    }

    if (result.status === 'success') {
      await saveSession(db, {
        ...session,
        step: 'ended',
        amount_pesewas: amountPesewas,
        provider,
        paystack_ref: reference
      });
      return ussdReply(userId, msisdn, `Payment of GHS ${ghs(amountPesewas)} successful!\nThank you for choosing TouchBase.`, false);
    }

    // Default: 'pay_offline' or 'pending' -> USSD prompt sent to user handset.
    // End session (MSGTYPE: false) so handset immediately displays MoMo PIN prompt.
    await saveSession(db, {
      ...session,
      step: 'ended',
      amount_pesewas: amountPesewas,
      provider,
      paystack_ref: reference
    });

    const approvalHint = provider === 'mtn'
      ? 'No prompt? Dial *170# > Wallet > 3. Approvals'
      : provider === 'vod'
        ? 'No prompt? Dial *110#'
        : 'Approve with your PIN';

    return ussdReply(
      userId,
      msisdn,
      `Payment request sent for GHS ${ghs(amountPesewas)}.\nApprove with your PIN.\n${approvalHint}`,
      false
    );
  } catch (err: any) {
    const errDetail = err?.body?.data?.gateway_response || err?.body?.data?.message || err?.body?.message || err?.message || String(err);
    console.error('[ussd] paystack charge failed', errDetail, JSON.stringify(err?.body || {}));
    throw new Error(`Paystack charge failed: ${errDetail}`);
  }
}
