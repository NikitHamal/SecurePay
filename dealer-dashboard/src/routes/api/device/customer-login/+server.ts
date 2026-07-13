import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import {
  computeStatus,
  errorResponse,
  generateDeviceApiSecret,
  getDb,
  getDealerSecurityPolicy,
  releaseApproved,
  releaseFields
} from '$lib/api/server';
import { verifyPassword } from '$lib/auth';

/**
 * Restores the per-device session after the DPC has been provisioned again.
 * Authentication is bound to all three values: customer account number, PIN,
 * and the inventory IMEI. A credential for another financed phone cannot be
 * used to take over this device.
 */
export const POST: RequestHandler = async ({ request, platform, locals }) => {
  if (!locals.hmacVerified || locals.hmacScope !== 'global') {
    return errorResponse('Bootstrap HMAC verification required', 401);
  }

  let body: Record<string, unknown>;
  try {
    body = await request.json() as Record<string, unknown>;
  } catch {
    return errorResponse('Request body must be valid JSON', 400);
  }

  const accountNumber = String(body.accountNumber ?? '').replace(/\D/g, '');
  const pin = String(body.pin ?? body.password ?? '').trim();
  const imei = String(body.imei ?? '').replace(/\D/g, '');

  if (accountNumber.length < 8 || accountNumber.length > 15) {
    return errorResponse('Enter the phone number used for registration', 400);
  }
  if (!/^\d{6,12}$/.test(pin)) {
    return errorResponse('Enter your 6 to 12 digit PIN', 400);
  }
  if (!/^\d{15}$/.test(imei)) {
    return errorResponse('The provisioned 15-digit IMEI is required', 400);
  }

  const db = getDb({ platform });
  const row = await db.prepare(`
    SELECT a.*, d.id AS linked_device_id, d.imei, d.model
    FROM accounts a
    JOIN devices d ON d.id = a.device_id
    WHERE a.customer_account_number = ? AND d.imei = ?
    LIMIT 1
  `).bind(accountNumber, imei).first<Record<string, unknown>>();

  // Deliberately use one generic error to avoid account/IMEI enumeration.
  const pinHash = String(row?.customer_pin_hash ?? '');
  if (!row || pinHash.length < 20 || !verifyPassword(pin, pinHash)) {
    return errorResponse('Account number, PIN, or device does not match', 401);
  }

  const nowSec = Math.floor(Date.now() / 1000);
  const deviceApiSecret = generateDeviceApiSecret();
  await db.prepare(`
    UPDATE accounts
       SET device_hmac_secret = ?,
           device_hmac_secret_created_at = ?,
           customer_pin_updated_at = COALESCE(customer_pin_updated_at, ?),
           updated_at = ?
     WHERE id = ?
  `).bind(deviceApiSecret, nowSec, nowSec, nowSec, String(row.id)).run();

  const release = releaseFields(row);
  const isStolen = Number(row.is_stolen ?? 0) === 1;
  const status = releaseApproved(row)
    ? 'ACTIVE'
    : (isStolen ? 'STOLEN' : (Number(row.locked_by_dealer ?? 0) === 1
      ? 'LOCKED'
      : computeStatus(Number(row.next_payment_due))));
  const securityPolicy = await getDealerSecurityPolicy({ platform }, String(row.dealer_id));

  return json({
    enrolled: true,
    activated: true,
    recovered: true,
    imei,
    apiSecret: deviceApiSecret,
    device: {
      id: String(row.linked_device_id),
      imei,
      model: String(row.model ?? ''),
      status: 'sold'
    },
    account: {
      id: String(row.id),
      customerName: String(row.customer_name ?? ''),
      status,
      nextPaymentDue: Number(row.next_payment_due),
      amountPaid: Number(row.amount_paid),
      totalLoanAmount: Number(row.total_loan_amount),
      dailyRate: Number(row.daily_rate),
      isStolen,
      releaseApproved: release.releaseApproved,
      releaseApprovedAt: release.releaseApprovedAt,
      releasedAt: release.releasedAt
    },
    securityPolicy,
    serverTime: Date.now()
  }, { headers: { 'Cache-Control': 'no-store' } });
};
