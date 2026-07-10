import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import {
  getDb,
  computeStatus,
  errorResponse,
  releaseFields,
  releaseApproved,
  getR2,
  generateAccountId
} from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';
import type { Customer, Status } from '$lib/types';
import { getScopeFilter } from '$lib/auth';

const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
const DAY_MS = 24 * 60 * 60 * 1000;

type ImageMime = 'image/jpeg' | 'image/png' | 'image/webp';

interface EncodedImage {
  bytes: Uint8Array;
  mimeType: ImageMime;
  extension: 'jpg' | 'png' | 'webp';
}

function cleanText(value: unknown): string {
  return String(value ?? '').trim();
}

function parseSafeInteger(value: unknown, fieldName: string, minimum: number): number {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < minimum) {
    throw new Error(`${fieldName} must be a whole number of pesewas and at least ${minimum}`);
  }
  return parsed;
}

function decodeImage(value: unknown, fieldName: string): EncodedImage | null {
  const input = cleanText(value);
  if (!input) return null;

  const match = input.match(/^data:image\/(jpeg|jpg|png|webp);base64,([A-Za-z0-9+/=\s]+)$/i);
  const mimeToken = match?.[1]?.toLowerCase() ?? 'jpeg';
  const payload = (match?.[2] ?? input).replace(/\s/g, '');

  if (!/^[A-Za-z0-9+/]*={0,2}$/.test(payload) || payload.length % 4 === 1) {
    throw new Error(`${fieldName} is not valid Base64 image data`);
  }

  let binary: string;
  try {
    binary = atob(payload);
  } catch {
    throw new Error(`${fieldName} is not valid Base64 image data`);
  }

  if (binary.length === 0 || binary.length > MAX_PHOTO_BYTES) {
    throw new Error(`${fieldName} must be between 1 byte and 5MB`);
  }

  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }

  if (mimeToken === 'png') return { bytes, mimeType: 'image/png', extension: 'png' };
  if (mimeToken === 'webp') return { bytes, mimeType: 'image/webp', extension: 'webp' };
  return { bytes, mimeType: 'image/jpeg', extension: 'jpg' };
}

export const GET: RequestHandler = async ({ locals, url, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const statusFilter = url.searchParams.get('status') as Status | null;
  const db = getDb({ platform });
  const scope = getScopeFilter(locals.dealer);

  const result = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE ${scope.where}
    ORDER BY a.created_at DESC
  `).bind(...scope.params).all();

  const customers: Customer[] = result.results.map((row) => {
    const nextPaymentDue = Number(row.next_payment_due);
    const amountPaid = Number(row.amount_paid);
    const totalLoanAmount = Number(row.total_loan_amount);
    const status: Status = releaseApproved(row as Record<string, unknown>)
      ? 'ACTIVE'
      : (row.is_stolen === 1 ? 'STOLEN' : (row.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(nextPaymentDue)));

    return {
      id: row.id as string,
      customerName: row.customer_name as string,
      nationalId: row.national_id as string,
      phoneNumber: row.phone_number as string,
      imei: row.imei as string,
      deviceModel: row.device_model as string,
      planName: row.plan_name as string,
      totalLoanAmount,
      amountPaid,
      remainingBalance: Math.max(0, totalLoanAmount - amountPaid),
      dailyRate: Number(row.daily_rate),
      nextPaymentDueEpochMillis: nextPaymentDue,
      status,
      isStolen: row.is_stolen === 1,
      customerPhotoPath: row.customer_photo_path as string | null,
      nationalIdFrontPath: row.national_id_front_path as string | null,
      nationalIdBackPath: row.national_id_back_path as string | null,
      termDays: Number(row.term_days),
      downPayment: Number(row.down_payment),
      enrolledBy: row.enrolled_by as string | null,
      ghanaCardVerified: row.ghana_card_verified === 1,
      ghanaCardStatus: row.ghana_card_status as string | null,
      ...releaseFields(row as Record<string, unknown>)
    };
  });

  return json(statusFilter ? customers.filter((customer) => customer.status === statusFilter) : customers);
};

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  let body: Record<string, unknown>;
  try {
    body = await request.json() as Record<string, unknown>;
  } catch {
    return errorResponse('Request body must be valid JSON', 400);
  }

  const customerName = cleanText(body.customerName);
  const nationalId = cleanText(body.nationalId).toUpperCase();
  const phoneNumber = cleanText(body.phoneNumber);
  const imei = cleanText(body.imei).replace(/\D/g, '');
  const planId = cleanText(body.planId) || null;

  if (customerName.length < 2 || customerName.length > 120) {
    return errorResponse('customerName must be between 2 and 120 characters', 400);
  }
  if (nationalId.length < 4 || nationalId.length > 64) {
    return errorResponse('nationalId must be between 4 and 64 characters', 400);
  }
  if (!/^[0-9+()\-\s]{8,24}$/.test(phoneNumber)) {
    return errorResponse('phoneNumber must contain 8 to 24 valid phone characters', 400);
  }
  if (!/^\d{15}$/.test(imei)) {
    return errorResponse('imei must contain exactly 15 digits', 400);
  }
  if (!planId && body.dailyRate == null) {
    return errorResponse('Either planId or custom dailyRate must be provided', 400);
  }

  let customerPhoto: EncodedImage | null;
  let nationalIdFront: EncodedImage | null;
  let nationalIdBack: EncodedImage | null;
  try {
    customerPhoto = decodeImage(body.customerPhoto, 'customerPhoto');
    nationalIdFront = decodeImage(body.nationalIdFront, 'nationalIdFront');
    nationalIdBack = decodeImage(body.nationalIdBack, 'nationalIdBack');
  } catch (error) {
    return errorResponse(error instanceof Error ? error.message : 'Invalid KYC image', 400);
  }

  const db = getDb({ platform });
  const device = await db.prepare(
    'SELECT id, imei, model, status FROM devices WHERE imei = ? AND dealer_id = ?'
  ).bind(imei, locals.dealer.id).first<{ id: string; imei: string; model: string; status: string }>();

  if (!device) return errorResponse('Device not found in your inventory', 404);
  if (device.status !== 'in_stock') return errorResponse('Device is not available for sale', 409);

  const plan = planId
    ? await db.prepare('SELECT id, total_amount, daily_rate, term_days, min_down_payment FROM plans WHERE id = ?')
      .bind(planId)
      .first<{ id: string; total_amount: number; daily_rate: number; term_days: number; min_down_payment: number }>()
    : null;
  if (planId && !plan) return errorResponse('Plan not found', 404);

  let totalLoanAmount: number;
  let dailyRate: number;
  let termDays: number;
  let downPayment: number;
  try {
    totalLoanAmount = plan
      ? parseSafeInteger(plan.total_amount, 'plan total amount', 1)
      : parseSafeInteger(body.totalAmount, 'totalAmount', 1);
    dailyRate = plan
      ? parseSafeInteger(plan.daily_rate, 'plan daily rate', 1)
      : parseSafeInteger(body.dailyRate, 'dailyRate', 1);
    termDays = plan
      ? parseSafeInteger(plan.term_days, 'plan term', 1)
      : parseSafeInteger(body.termDays, 'termDays', 1);
    const minimumDownPayment = plan ? parseSafeInteger(plan.min_down_payment, 'plan minimum down payment', 0) : 0;
    downPayment = body.downPayment == null || body.downPayment === ''
      ? minimumDownPayment
      : parseSafeInteger(body.downPayment, 'downPayment', minimumDownPayment);
  } catch (error) {
    return errorResponse(error instanceof Error ? error.message : 'Invalid loan values', 400);
  }

  if (downPayment > totalLoanAmount) {
    return errorResponse('downPayment cannot exceed total loan amount', 400);
  }

  const nowMillis = Date.now();
  const nowSeconds = Math.floor(nowMillis / 1000);
  const nextPaymentDue = nowMillis + DAY_MS;
  const accountId = generateAccountId();
  const r2 = getR2({ platform });
  const uploadedKeys: string[] = [];

  const uploadImage = async (image: EncodedImage | null, label: string): Promise<string | null> => {
    if (!image) return null;
    const key = `kyc/customer_${accountId}_${label}.${image.extension}`;
    await r2.put(key, image.bytes, { httpMetadata: { contentType: image.mimeType } });
    uploadedKeys.push(key);
    return key;
  };

  let customerPhotoPath: string | null;
  let nationalIdFrontPath: string | null;
  let nationalIdBackPath: string | null;
  try {
    customerPhotoPath = await uploadImage(customerPhoto, 'photo');
    nationalIdFrontPath = await uploadImage(nationalIdFront, 'id_front');
    nationalIdBackPath = await uploadImage(nationalIdBack, 'id_back');
  } catch (error) {
    await Promise.allSettled(uploadedKeys.map((key) => r2.delete(key)));
    console.error('KYC upload failed', error);
    return errorResponse('Unable to store KYC images. No customer account was created.', 502);
  }

  const statements = [
    db.prepare(`
      INSERT INTO accounts (
        id, customer_name, national_id, phone_number, device_id, dealer_id, plan_id,
        total_loan_amount, amount_paid, daily_rate, next_payment_due, status,
        locked_by_dealer, down_payment, term_days, currency_code, customer_photo_path,
        national_id_front_path, national_id_back_path, enrolled_by, branch_id, agency_id,
        created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0, ?, ?, 'GHS', ?, ?, ?, ?, ?, ?, ?, ?)
    `).bind(
      accountId,
      customerName,
      nationalId,
      phoneNumber,
      device.id,
      locals.dealer.id,
      planId,
      totalLoanAmount,
      downPayment,
      dailyRate,
      nextPaymentDue,
      downPayment,
      termDays,
      customerPhotoPath,
      nationalIdFrontPath,
      nationalIdBackPath,
      locals.dealer.id,
      locals.dealer.branchId || null,
      locals.dealer.agencyId || null,
      nowSeconds,
      nowSeconds
    ),
    db.prepare("UPDATE devices SET status = 'sold' WHERE id = ? AND status = 'in_stock'").bind(device.id)
  ];

  if (downPayment > 0) {
    statements.push(db.prepare(`
      INSERT INTO payments (id, account_id, amount, method, reference, recorded_by, created_at)
      VALUES (?, ?, ?, 'cash', 'Down payment', ?, ?)
    `).bind(uuidv4(), accountId, downPayment, locals.dealer.id, nowSeconds));
  }

  try {
    await db.batch(statements);
  } catch (error) {
    await Promise.allSettled(uploadedKeys.map((key) => r2.delete(key)));
    console.error('Customer enrollment transaction failed', error);
    const message = String(error).toLowerCase();
    const conflict = message.includes('unique') || message.includes('constraint');
    return errorResponse(
      conflict ? 'This device or customer enrollment already exists' : 'Customer enrollment failed. No sale was recorded.',
      conflict ? 409 : 500
    );
  }

  const adminRecipients = await db.prepare(`
    SELECT id FROM dealers
    WHERE role = 'SUPER_ADMIN' OR (role = 'AGENCY_OWNER' AND agency_id = ?)
  `).bind(locals.dealer.agencyId || '').all<{ id: string }>();

  const notificationStatements = adminRecipients.results
    .filter((admin) => admin.id !== locals.dealer?.id)
    .map((admin) => db.prepare(`
      INSERT INTO notifications (
        id, recipient_id, type, title, message, related_entity_type, related_entity_id, created_at
      ) VALUES (?, ?, 'NEW_SALE', ?, ?, 'account', ?, ?)
    `).bind(
      uuidv4(),
      admin.id,
      'New Sale Recorded',
      `${locals.dealer!.name} enrolled customer ${customerName} for ${imei} (GH₵${(downPayment / 100).toFixed(2)} down payment)`,
      accountId,
      nowSeconds
    ));
  if (notificationStatements.length > 0) await db.batch(notificationStatements);

  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ?
  `).bind(accountId).first();

  if (!row) return errorResponse('Account was created but could not be reloaded', 500);

  const amountPaid = Number(row.amount_paid);
  const total = Number(row.total_loan_amount);
  const status: Status = releaseApproved(row as Record<string, unknown>)
    ? 'ACTIVE'
    : computeStatus(Number(row.next_payment_due));

  const customer: Customer = {
    id: row.id as string,
    customerName: row.customer_name as string,
    nationalId: row.national_id as string,
    phoneNumber: row.phone_number as string,
    imei: row.imei as string,
    deviceModel: row.device_model as string,
    planName: (row.plan_name as string) || 'Custom',
    totalLoanAmount: total,
    amountPaid,
    remainingBalance: Math.max(0, total - amountPaid),
    dailyRate: Number(row.daily_rate),
    nextPaymentDueEpochMillis: Number(row.next_payment_due),
    status,
    isStolen: row.is_stolen === 1,
    customerPhotoPath: row.customer_photo_path as string | null,
    nationalIdFrontPath: row.national_id_front_path as string | null,
    nationalIdBackPath: row.national_id_back_path as string | null,
    termDays: Number(row.term_days),
    downPayment,
    enrolledBy: locals.dealer.id,
    ghanaCardVerified: false,
    ghanaCardStatus: null,
    ...releaseFields(row as Record<string, unknown>)
  };

  return json(customer, { status: 201 });
};
