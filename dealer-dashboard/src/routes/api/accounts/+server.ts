import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse, releaseFields, releaseApproved, getR2, generateAccountId } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';
import type { Customer, Status } from '$lib/types';
import { getScopeFilter } from '$lib/auth';

const MAX_PHOTO_BYTES = 5 * 1024 * 1024;

function validatePhotoSize(base64Data: string | undefined | null, fieldName: string): string | null {
  if (!base64Data) return null;
  const sizeEstimate = (base64Data.length * 3) / 4;
  if (sizeEstimate > MAX_PHOTO_BYTES) {
    return `${fieldName} exceeds 5MB limit`;
  }
  return null;
}

export const GET: RequestHandler = async ({ locals, url, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

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

  if (statusFilter) {
    const filtered = customers.filter((c) => c.status === statusFilter);
    return json(filtered);
  }

  return json(customers);
};

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const body = await request.json();
  const {
    customerName,
    nationalId,
    phoneNumber,
    imei,
    planId,
    dailyRate: customDailyRate,
    totalAmount: customTotalAmount,
    termDays: customTermDays,
    downPayment,
    customerPhoto,
    nationalIdFront,
    nationalIdBack
  } = body;

  if (!customerName || !nationalId || !phoneNumber || !imei) {
    return errorResponse('Missing required fields: customerName, nationalId, phoneNumber, imei', 400);
  }

  if (!planId && !customDailyRate) {
    return errorResponse('Either planId or dailyRate must be provided', 400);
  }

  const photoError = validatePhotoSize(customerPhoto, 'customerPhoto')
    || validatePhotoSize(nationalIdFront, 'nationalIdFront')
    || validatePhotoSize(nationalIdBack, 'nationalIdBack');
  if (photoError) {
    return errorResponse(photoError, 413);
  }

  const db = getDb({ platform });

  const device = await db.prepare('SELECT id, imei, model, status FROM devices WHERE imei = ? AND dealer_id = ?').bind(imei, locals.dealer.id).first();

  if (!device) {
    return errorResponse('Device not found in your inventory', 404);
  }

  if (device.status === 'sold') {
    return errorResponse('Device is already sold', 409);
  }

  let plan = null;
  if (planId) {
    plan = await db.prepare('SELECT * FROM plans WHERE id = ?').bind(planId).first();
    if (!plan) {
      return errorResponse('Plan not found', 404);
    }
  }

  const dp = Number(downPayment) || (plan ? Number(plan.min_down_payment) : 0);
  const totalLoanAmount = Number(customTotalAmount) || (plan ? Number(plan.total_amount) : 0);
  const dailyRate = Number(customDailyRate) || (plan ? Number(plan.daily_rate) : 0);
  const termDays = Number(customTermDays) || (plan ? Number(plan.term_days) : 0);
  const now = Date.now();
  const nextPaymentDue = now + 24 * 60 * 60 * 1000;

  const accountId = generateAccountId();

  const uploadBase64ToR2 = async (base64Data: string | undefined | null, key: string): Promise<string | null> => {
    if (!base64Data) return null;
    try {
      const r2 = getR2({ platform });
      const cleanBase64 = base64Data.replace(/^data:image\/[a-z]+;base64,/, '');
      const binaryString = atob(cleanBase64);
      const len = binaryString.length;
      const bytes = new Uint8Array(len);
      for (let i = 0; i < len; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      await r2.put(key, bytes, {
        httpMetadata: { contentType: 'image/jpeg' }
      });
      return key;
    } catch (err) {
      console.error('Error uploading R2 image:', err);
      return null;
    }
  };

  const customerPhotoPath = await uploadBase64ToR2(customerPhoto, `kyc/customer_${accountId}_photo.jpg`);
  const nationalIdFrontPath = await uploadBase64ToR2(nationalIdFront, `kyc/customer_${accountId}_id_front.jpg`);
  const nationalIdBackPath = await uploadBase64ToR2(nationalIdBack, `kyc/customer_${accountId}_id_back.jpg`);

  await db.prepare(
    `INSERT INTO accounts (id, customer_name, national_id, phone_number, device_id, dealer_id, plan_id, total_loan_amount, amount_paid, daily_rate, next_payment_due, status, locked_by_dealer, down_payment, term_days, currency_code, customer_photo_path, national_id_front_path, national_id_back_path, enrolled_by, branch_id, agency_id, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    accountId, customerName, nationalId, phoneNumber, device.id as string,
    locals.dealer.id, planId || null, totalLoanAmount, dp, dailyRate, nextPaymentDue,
    'ACTIVE', 0, dp, termDays, 'GHS', customerPhotoPath, nationalIdFrontPath, nationalIdBackPath,
    locals.dealer.id,
    locals.dealer.branchId || null,
    locals.dealer.agencyId || null,
    Math.floor(now / 1000), Math.floor(now / 1000)
  ).run();

  await db.prepare("UPDATE devices SET status = 'sold' WHERE id = ?").bind(device.id as string).run();

  if (dp > 0) {
    await db.prepare(
      `INSERT INTO payments (id, account_id, amount, method, reference, recorded_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).bind(uuidv4(), accountId, dp, 'cash', 'Down payment', locals.dealer.id, Math.floor(now / 1000)).run();
  }

  // Notify Super Admins and Agency Owners about the new sale
  const notifId = uuidv4();
  const adminRecipients = await db.prepare(`
    SELECT id FROM dealers
    WHERE role = 'SUPER_ADMIN' OR (role = 'AGENCY_OWNER' AND agency_id = ?)
  `).bind(locals.dealer.agencyId || '').all();

  for (const admin of adminRecipients.results) {
    if (admin.id !== locals.dealer.id) {
      await db.prepare(`
        INSERT INTO notifications (id, recipient_id, type, title, message, related_entity_type, related_entity_id, created_at)
        VALUES (?, ?, 'NEW_SALE', ?, ?, 'account', ?, ?)
      `).bind(
        uuidv4(),
        admin.id as string,
        'New Sale Recorded',
        `${locals.dealer.name} enrolled customer ${customerName} for ${imei} (GH₵${(dp / 100).toFixed(2)} down payment)`,
        accountId,
        Math.floor(now / 1000)
      ).run();
    }
  }

  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ?
  `).bind(accountId).first();

  const amtPaid = Number(row!.amount_paid);
  const totalLoan = Number(row!.total_loan_amount);
  const status: Status = releaseApproved(row as Record<string, unknown>)
    ? 'ACTIVE'
    : computeStatus(Number(row!.next_payment_due));

  const customer: Customer = {
    id: row!.id as string,
    customerName: row!.customer_name as string,
    nationalId: row!.national_id as string,
    phoneNumber: row!.phone_number as string,
    imei: row!.imei as string,
    deviceModel: row!.device_model as string,
    planName: row!.plan_name as string || 'Custom',
    totalLoanAmount: totalLoan,
    amountPaid: amtPaid,
    remainingBalance: Math.max(0, totalLoan - amtPaid),
    dailyRate: Number(row!.daily_rate),
    nextPaymentDueEpochMillis: Number(row!.next_payment_due),
    status,
    isStolen: row!.is_stolen === 1,
    customerPhotoPath: row!.customer_photo_path as string | null,
    nationalIdFrontPath: row!.national_id_front_path as string | null,
    nationalIdBackPath: row!.national_id_back_path as string | null,
    termDays: Number(row!.term_days),
    downPayment: dp,
    enrolledBy: locals.dealer.id,
    ghanaCardVerified: false,
    ghanaCardStatus: null,
    ...releaseFields(row as Record<string, unknown>)
  };

  return json(customer, { status: 201 });
};
