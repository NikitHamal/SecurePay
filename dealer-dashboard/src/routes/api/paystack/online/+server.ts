import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, getPaystackSecret } from '$lib/api/server';
import { initializeCharge, generateReference, hasPaystackConfigured } from '$lib/paystack';
import { getCustomerEmail } from '$lib/paystack/email';

const MAX_AMOUNT_GHS = 50_000;

function phoneToProvider(phone: string): 'mtn' | 'vod' | 'atl' {
  const digits = phone.replace(/\D/g, '');
  const prefix = digits.startsWith('233') ? '0' + digits.slice(3, 5) : digits.slice(0, 3);
  if (['024', '054', '055', '059', '053', '025'].includes(prefix)) return 'mtn';
  if (['020', '050'].includes(prefix)) return 'vod';
  if (['026', '056', '027', '057'].includes(prefix)) return 'atl';
  return 'mtn';
}

export const POST: RequestHandler = async ({ request, platform }) => {
  const secret = getPaystackSecret({ platform });
  if (!hasPaystackConfigured(secret)) {
    return json({ ok: false, message: 'Paystack is temporarily unavailable. Please try again shortly.' }, { status: 503 });
  }

  const body = await request.json().catch(() => ({})) as Record<string, unknown>;
  const accountNumber = String(body.account_number ?? body.accountNumber ?? '').trim();
  const amountGhs = Number(body.amount);
  const phone = String(body.phone ?? '').trim();

  if (!accountNumber) {
    return json({ ok: false, message: 'Account number is required.' }, { status: 400 });
  }
  if (!Number.isFinite(amountGhs) || amountGhs <= 0) {
    return json({ ok: false, message: 'A positive payment amount is required.' }, { status: 400 });
  }
  if (amountGhs > MAX_AMOUNT_GHS) {
    return json({ ok: false, message: `Payment amount exceeds maximum limit of GHS ${MAX_AMOUNT_GHS.toLocaleString()}.` }, { status: 400 });
  }

  let normalizedPhone = phone.replace(/\s+/g, '');
  if (normalizedPhone.startsWith('0')) normalizedPhone = '+233' + normalizedPhone.slice(1);
  if (!normalizedPhone.startsWith('+')) normalizedPhone = '+' + normalizedPhone;

  if (!/^\+233\d{9}$/.test(normalizedPhone)) {
    return json({ ok: false, message: 'A valid Ghana mobile money number is required (e.g. 0551234567).' }, { status: 400 });
  }

  const db = getDb({ platform });

  // Lookup account
  const cleanPhone = accountNumber.replace(/\D/g, '');
  const phoneVariants = [
    accountNumber,
    cleanPhone,
    cleanPhone.startsWith('233') ? '0' + cleanPhone.slice(3) : cleanPhone,
    cleanPhone.startsWith('0') ? '233' + cleanPhone.slice(1) : cleanPhone,
    cleanPhone.startsWith('0') ? '+233' + cleanPhone.slice(1) : cleanPhone
  ].filter(Boolean);

  const placeholders = phoneVariants.map(() => '?').join(',');

  const account = await db.prepare(`
    SELECT a.id, a.customer_name, a.customer_account_number, a.phone_number,
           a.total_loan_amount, a.amount_paid, a.daily_rate, a.dealer_id, d.imei
      FROM accounts a
      LEFT JOIN devices d ON d.id = a.device_id
     WHERE a.customer_account_number = ?
        OR a.phone_number IN (${placeholders})
     LIMIT 1
  `).bind(accountNumber, ...phoneVariants).first<Record<string, any>>();

  if (!account) {
    return json({ ok: false, message: 'Account not found. Please check your account number.' }, { status: 404 });
  }

  const totalLoanPesewas = Number(account.total_loan_amount) || 0;
  const amountPaidPesewas = Number(account.amount_paid) || 0;
  const remainingPesewas = Math.max(0, totalLoanPesewas - amountPaidPesewas);
  const amountPesewas = Math.round(amountGhs * 100);

  if (amountPesewas > remainingPesewas + 100) { // allow 1 GHS rounding buffer
    return json({
      ok: false,
      message: `Amount exceeds remaining balance of GHS ${(remainingPesewas / 100).toFixed(2)}.`
    }, { status: 409 });
  }

  const provider = phoneToProvider(normalizedPhone);
  const email = getCustomerEmail(account, normalizedPhone);
  const reference = generateReference('SPO'); // SPO = SP Online
  const nowSec = Math.floor(Date.now() / 1000);

  try {
    const result = await initializeCharge({
      amount: amountPesewas,
      email,
      currency: 'GHS',
      reference,
      channels: ['mobile_money'],
      metadata: {
        account_id: account.id,
        account_number: account.customer_account_number,
        dealer_id: account.dealer_id,
        customer_name: account.customer_name,
        source: 'touchbase-online'
      },
      mobile_money: {
        phone: normalizedPhone,
        provider: provider as any
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
      normalizedPhone,
      result.status || 'pending',
      typeof result.message === 'string' ? result.message.slice(0, 500) : (result.display_text || null),
      JSON.stringify({ account_id: account.id, source: 'touchbase-online' }),
      nowSec,
      nowSec
    ).run().catch((e) => console.error('[paystack/online] insert transaction failed', e));

    return json({
      ok: true,
      reference,
      status: result.status,
      message: result.display_text || result.message || 'Payment request sent. Please approve with your MoMo PIN on your phone.',
      authorization_url: (result as any).authorization_url || null
    });
  } catch (err: any) {
    const detail = err?.body?.data?.gateway_response || err?.body?.data?.message || err?.body?.message || err?.message || 'Payment could not be started.';
    return json({ ok: false, message: detail }, { status: 400 });
  }
};
