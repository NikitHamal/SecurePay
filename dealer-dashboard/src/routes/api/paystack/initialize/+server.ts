import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, getDb, getPaystackSecret } from '$lib/api/server';
import { getAccountScopeFilter } from '$lib/auth';
import { initializeCharge, generateReference, hasPaystackConfigured } from '$lib/paystack';
import { getCustomerEmail } from '$lib/paystack/email';

const MAX_AMOUNT_GHS = 50_000; // safety cap — GH₵ 50,000

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const secret = getPaystackSecret({ platform });
  if (!hasPaystackConfigured(secret)) {
    return errorResponse('Paystack is not configured. Set PAYSTACK_SECRET_KEY in your environment.', 503);
  }

  const db = getDb({ platform });
  const scope = getAccountScopeFilter(locals.dealer, 'a');

  const body = await request.json().catch(() => ({})) as Record<string, unknown>;
  const accountId = String(body.accountId ?? '').trim();
  const amountGhs = Number(body.amount);
  const phone = String(body.phone ?? '').trim();
  const provider = String(body.provider ?? 'mtn').trim().toLowerCase();
  const channel = String(body.channel ?? 'mobile_money').trim().toLowerCase();

  if (!accountId) return errorResponse('Account ID is required', 400);
  if (!Number.isFinite(amountGhs) || amountGhs <= 0) return errorResponse('A positive amount is required', 400);
  if (amountGhs > MAX_AMOUNT_GHS) return errorResponse(`Amount exceeds GH₵ ${MAX_AMOUNT_GHS.toLocaleString()}`, 400);

  const amountPesewas = Math.round(amountGhs * 100);

  const allowedProviders = ['mtn', 'vod', 'tgo'];
  if (!allowedProviders.includes(provider)) {
    return errorResponse(`Provider must be one of: ${allowedProviders.join(', ')}`, 400);
  }
  if (!/^0?[25]\d{8}$/.test(phone) && !/^\+233\d{9}$/.test(phone)) {
    return errorResponse('A valid Ghana phone number is required (e.g. 055xxxxxxx or +23355xxxxxxx)', 400);
  }

  const account = await db.prepare(
    `SELECT a.id, a.customer_name, a.phone_number, a.national_id, a.amount_paid, a.total_loan_amount,
            a.daily_rate, a.dealer_id, d.imei
       FROM accounts a
       JOIN devices d ON d.id = a.device_id
      WHERE a.id = ? AND ${scope.where}`
  ).bind(accountId, ...scope.params).first<Record<string, any>>();

  if (!account) return errorResponse('Account not found', 404);

  const remaining = Math.max(0, Number(account.total_loan_amount) - Number(account.amount_paid));
  if (amountPesewas > remaining + 1) {
    return errorResponse(`Amount exceeds remaining balance of GH₵ ${(remaining / 100).toFixed(2)}`, 409);
  }

  // Normalize the phone number to the +233... form Paystack expects for Ghana MoMo.
  let normalizedPhone = phone.replace(/\s+/g, '');
  if (normalizedPhone.startsWith('0')) normalizedPhone = '+233' + normalizedPhone.slice(1);
  if (!normalizedPhone.startsWith('+')) normalizedPhone = '+' + normalizedPhone;

  const email = getCustomerEmail(account, normalizedPhone);
  const reference = generateReference('SP');

  try {
    const result = await initializeCharge({
      amount: amountPesewas,
      email,
      currency: 'GHS',
      reference,
      channels: channel === 'card' ? ['card'] : ['mobile_money'],
      metadata: {
        account_id: accountId,
        dealer_id: locals.dealer.id,
        imei: account.imei,
        customer_name: account.customer_name
      },
      mobile_money: channel === 'card' ? undefined : {
        phone: normalizedPhone,
        provider: provider as any
      }
    }, secret);

    // Persist the transaction record.
    const nowSec = Math.floor(Date.now() / 1000);
    await db.prepare(`
      INSERT INTO paystack_transactions
        (id, reference, access_code, account_id, dealer_id, amount, currency, channel, provider,
         customer_email, customer_phone, status, gateway_response, metadata_json, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      result.id ?? null,
      reference,
      result.access_code ?? null,
      accountId,
      locals.dealer.id,
      amountPesewas,
      'GHS',
      result.authorization?.channel || channel,
      channel === 'card' ? 'card' : provider,
      email,
      normalizedPhone,
      result.status || 'pending',
      typeof result.message === 'string' ? result.message.slice(0, 500) : null,
      JSON.stringify({
        account_id: accountId,
        imei: account.imei,
        customer_name: account.customer_name
      }),
      nowSec,
      nowSec
    ).run();

    return json({
      ok: true,
      reference,
      accessCode: result.access_code || null,
      status: result.status,
      displayText: result.display_text || result.message || 'Follow the prompt on your phone to complete the payment.',
      customerEmail: email,
      amount: amountPesewas,
      provider,
      phone: normalizedPhone,
      otpRequired: result.status === 'send_otp' || result.status === 'otp_sent'
    });
  } catch (err: any) {
    console.error('Paystack init failed', err);
    return errorResponse(err.message || 'Failed to initialize payment', 502);
  }
};
