import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, getDb, getPaystackSecret } from '$lib/api/server';
import { initializeCharge, generateReference, hasPaystackConfigured } from '$lib/paystack';
import { getCustomerEmail } from '$lib/paystack/email';

const MAX_AMOUNT_GHS = 50_000;

/**
 * POST /api/device/paystack/initialize
 *
 * Device/HMAC-authenticated endpoint used by the Android customer app to start
 * a Paystack mobile-money charge for the provisioned account. Scope is strictly
 * enforced by accountId+imei against the device_hmac_secret lookup in hooks.
 */
export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.hmacVerified || locals.hmacScope !== 'device') {
    return errorResponse('Device HMAC verification required', 401);
  }

  const secret = getPaystackSecret({ platform });
  if (!hasPaystackConfigured(secret)) {
    return errorResponse('Paystack is not configured', 503);
  }

  const body = await request.json().catch(() => ({})) as Record<string, unknown>;
  const accountId = String(body.accountId ?? locals.deviceId ?? '').trim();
  const imei = String(body.imei ?? locals.deviceImei ?? '').trim();
  const amountGhs = Number(body.amount);
  const phone = String(body.phone ?? '').trim();
  const provider = String(body.provider ?? 'mtn').trim().toLowerCase();

  if (!accountId) return errorResponse('Account ID is required', 400);
  if (!/^\d{15}$/.test(imei)) return errorResponse('A valid 15-digit IMEI is required', 400);
  if (!Number.isFinite(amountGhs) || amountGhs <= 0) return errorResponse('A positive amount is required', 400);
  if (amountGhs > MAX_AMOUNT_GHS) return errorResponse(`Amount exceeds GH₵ ${MAX_AMOUNT_GHS.toLocaleString()}`, 400);

  const allowedProviders = ['mtn', 'vod', 'tgo'];
  if (!allowedProviders.includes(provider)) {
    return errorResponse(`Provider must be one of: ${allowedProviders.join(', ')}`, 400);
  }
  if (!/^0?[25]\d{8}$/.test(phone) && !/^\+233\d{9}$/.test(phone)) {
    return errorResponse('A valid Ghana phone number is required (e.g. 055xxxxxxx or +23355xxxxxxx)', 400);
  }

  const amountPesewas = Math.round(amountGhs * 100);
  const db = getDb({ platform });

  const account = await db.prepare(`
    SELECT a.id, a.customer_name, a.phone_number, a.national_id, a.email,
           a.amount_paid, a.total_loan_amount, a.daily_rate, a.dealer_id, d.imei
      FROM accounts a
      JOIN devices d ON d.id = a.device_id
     WHERE a.id = ? AND d.imei = ?
  `).bind(accountId, imei).first<Record<string, any>>();

  if (!account) return errorResponse('Device account not found', 404);

  const remaining = Math.max(0, Number(account.total_loan_amount) - Number(account.amount_paid));
  if (amountPesewas > remaining + 1) {
    return errorResponse(`Amount exceeds remaining balance of GH₵ ${(remaining / 100).toFixed(2)}`, 409);
  }

  let normalizedPhone = phone.replace(/\s+/g, '');
  if (normalizedPhone.startsWith('0')) normalizedPhone = '+233' + normalizedPhone.slice(1);
  if (!normalizedPhone.startsWith('+')) normalizedPhone = '+' + normalizedPhone;

  const email = getCustomerEmail(account, normalizedPhone);
  const reference = generateReference('SPD'); // SPD = SP Device

  try {
    const result = await initializeCharge({
      amount: amountPesewas,
      email,
      currency: 'GHS',
      reference,
      channels: ['mobile_money'],
      metadata: {
        account_id: accountId,
        dealer_id: account.dealer_id,
        imei,
        customer_name: account.customer_name,
        source: 'customer-app'
      },
      mobile_money: {
        phone: normalizedPhone,
        provider: provider as any
      }
    }, secret);

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
      account.dealer_id,
      amountPesewas,
      'GHS',
      result.authorization?.channel || 'mobile_money',
      provider,
      email,
      normalizedPhone,
      result.status || 'pending',
      typeof result.message === 'string' ? result.message.slice(0, 500) : null,
      JSON.stringify({ account_id: accountId, imei, customer_name: account.customer_name, source: 'customer-app' }),
      nowSec,
      nowSec
    ).run();

    return json({
      ok: true,
      reference,
      accessCode: result.access_code || null,
      status: result.status,
      displayText: result.display_text || result.message || 'Approve the prompt on your phone to complete payment.',
      customerEmail: email,
      amount: amountPesewas,
      provider,
      phone: normalizedPhone,
      otpRequired: result.status === 'send_otp' || result.status === 'otp_sent'
    });
  } catch (err: any) {
    console.error('Device Paystack init failed', err);
    return errorResponse(err.message || 'Failed to initialize payment', 502);
  }
};
