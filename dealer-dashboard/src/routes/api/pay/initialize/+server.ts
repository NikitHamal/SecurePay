import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, getDb, getPaystackSecret } from '$lib/api/server';
import { initializeCharge, initializeTransaction, generateReference, hasPaystackConfigured } from '$lib/paystack';
import { getCustomerEmail } from '$lib/paystack/email';

const MAX_AMOUNT_GHS = 50_000;

export const POST: RequestHandler = async ({ request, platform, url }) => {
  const secret = getPaystackSecret({ platform });
  if (!hasPaystackConfigured(secret)) {
    return errorResponse('Payment gateway is temporarily unavailable', 503);
  }

  const body = await request.json().catch(() => ({})) as Record<string, unknown>;
  const accountId = String(body.accountId ?? '').trim();
  const amountGhs = Number(body.amount);
  const channel = String(body.channel ?? 'mobile_money').trim().toLowerCase();
  const phone = String(body.phone ?? '').trim();
  const provider = String(body.provider ?? 'mtn').trim().toLowerCase();
  const customEmail = typeof body.email === 'string' && body.email.includes('@') ? body.email.trim() : '';

  if (!accountId) return errorResponse('Account ID is required', 400);
  if (!Number.isFinite(amountGhs) || amountGhs <= 0) return errorResponse('A positive amount is required', 400);
  if (amountGhs > MAX_AMOUNT_GHS) return errorResponse(`Amount exceeds GH₵ ${MAX_AMOUNT_GHS.toLocaleString()}`, 400);

  const amountPesewas = Math.round(amountGhs * 100);
  const db = getDb({ platform });

  const account = await db.prepare(`
    SELECT a.id, a.customer_name, a.customer_account_number, a.phone_number,
           a.total_loan_amount, a.amount_paid, a.daily_rate, a.dealer_id, d.imei
      FROM accounts a
      LEFT JOIN devices d ON d.id = a.device_id
     WHERE a.id = ?
  `).bind(accountId).first<Record<string, any>>();

  if (!account) return errorResponse('Account not found', 404);

  const remaining = Math.max(0, Number(account.total_loan_amount) - Number(account.amount_paid));
  if (amountPesewas > remaining + 1) {
    return errorResponse(`Amount exceeds remaining balance of GH₵ ${(remaining / 100).toFixed(2)}`, 409);
  }

  const reference = generateReference('SPW'); // SPW = SecurePay Web
  const nowSec = Math.floor(Date.now() / 1000);

  if (channel === 'card') {
    const userEmail = customEmail || getCustomerEmail(account, account.phone_number || '0000000000');
    const callbackUrl = `${url.origin}/pay/verify?reference=${encodeURIComponent(reference)}`;

    const tx = await initializeTransaction({
      amount: amountPesewas,
      email: userEmail,
      currency: 'GHS',
      reference,
      callback_url: callbackUrl,
      channels: ['card', 'mobile_money', 'bank_transfer'],
      metadata: {
        account_id: account.id,
        account_number: account.customer_account_number,
        dealer_id: account.dealer_id,
        customer_name: account.customer_name,
        source: 'web-portal'
      }
    }, secret);

    await db.prepare(`
      INSERT INTO paystack_transactions
        (id, reference, access_code, account_id, dealer_id, amount, currency, channel, provider,
         customer_email, customer_phone, status, gateway_response, metadata_json, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      null,
      reference,
      tx.access_code || null,
      account.id,
      account.dealer_id,
      amountPesewas,
      'GHS',
      'card',
      'card',
      userEmail,
      account.phone_number || null,
      'pending',
      'Checkout initialized',
      JSON.stringify({ account_id: account.id, source: 'web-portal' }),
      nowSec,
      nowSec
    ).run().catch((e) => console.error('[pay] transaction insert failed', e));

    return json({
      mode: 'redirect',
      reference,
      authorizationUrl: tx.authorization_url
    });
  }

  // Mobile Money direct push
  const allowedProviders = ['mtn', 'vod', 'atl', 'tgo'];
  if (!allowedProviders.includes(provider)) {
    return errorResponse('Provider must be one of: mtn, vod, atl', 400);
  }
  const mappedProvider = provider === 'tgo' ? 'atl' : provider;

  let normalizedPhone = phone.replace(/\s+/g, '');
  if (normalizedPhone.startsWith('0')) normalizedPhone = '+233' + normalizedPhone.slice(1);
  if (!normalizedPhone.startsWith('+')) normalizedPhone = '+' + normalizedPhone;

  if (!/^\+233\d{9}$/.test(normalizedPhone)) {
    return errorResponse('A valid Ghana mobile number is required (e.g. 055xxxxxxx)', 400);
  }

  const email = customEmail || getCustomerEmail(account, normalizedPhone);

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
        source: 'web-portal'
      },
      mobile_money: {
        phone: normalizedPhone,
        provider: mappedProvider as any
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
      mappedProvider,
      email,
      normalizedPhone,
      result.status || 'pending',
      typeof result.message === 'string' ? result.message.slice(0, 500) : (result.display_text || null),
      JSON.stringify({ account_id: account.id, source: 'web-portal' }),
      nowSec,
      nowSec
    ).run().catch((e) => console.error('[pay] transaction insert failed', e));

    return json({
      mode: 'momo',
      reference,
      status: result.status,
      displayText: result.display_text || 'Payment request sent. Please approve the MoMo prompt on your phone.',
      otpRequired: result.status === 'send_otp'
    });
  } catch (err: any) {
    const detail = err?.body?.data?.gateway_response || err?.body?.data?.message || err?.body?.message || err?.message || 'Payment initiation failed';
    return errorResponse(detail, 400);
  }
};
