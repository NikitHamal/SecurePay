import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse, releaseFields, releaseApproved, getR2 } from '$lib/api/server';
import { sendFcm } from '$lib/api/fcm';
import { v4 as uuidv4 } from 'uuid';
import type { Customer, Status } from '$lib/types';
import { getAccountScopeFilter, canReleaseOrDeleteAccount } from '$lib/auth';

export const GET: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const db = getDb({ platform });
  const scope = getAccountScopeFilter(locals.dealer, 'a');
  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ? AND ${scope.where}
  `).bind(params.id, ...scope.params).first();

  if (!row) {
    return errorResponse('Account not found', 404);
  }

  const nextPaymentDue = Number(row.next_payment_due);
  const amountPaid = Number(row.amount_paid);
  const totalLoanAmount = Number(row.total_loan_amount);
  const status: Status = releaseApproved(row as Record<string, unknown>)
    ? 'ACTIVE'
    : (row.is_stolen === 1 ? 'STOLEN' : (row.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(nextPaymentDue)));

  const customer: Customer = {
    id: row.id as string,
    customerName: row.customer_name as string,
    nationalId: row.national_id as string,
    phoneNumber: row.phone_number as string,
    imei: row.imei as string,
    deviceModel: row.device_model as string,
    planName: row.plan_name as string || 'Custom',
    totalLoanAmount,
    amountPaid,
    remainingBalance: Math.max(0, totalLoanAmount - amountPaid),
    dailyRate: Number(row.daily_rate),
    nextPaymentDueEpochMillis: nextPaymentDue,
    status,
    lockedByDealer: Number(row.locked_by_dealer ?? 0),
    isStolen: row.is_stolen === 1,
    customerPhotoPath: row.customer_photo_path as string | null,
    nationalIdFrontPath: row.national_id_front_path as string | null,
    nationalIdBackPath: row.national_id_back_path as string | null,
    termDays: Number(row.term_days),
    downPayment: Number(row.down_payment),
    ...releaseFields(row as Record<string, unknown>)
  };


  return json(customer);
};

export const PATCH: RequestHandler = async ({ locals, params, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const db = getDb({ platform });
  const scope = getAccountScopeFilter(locals.dealer, 'a');
  const authorized = await db.prepare(`SELECT a.id FROM accounts a WHERE a.id = ? AND ${scope.where}`)
    .bind(params.id, ...scope.params)
    .first<{ id: string }>();
  if (!authorized) return errorResponse('Account not found', 404);

  const body = await request.json();
  const {
    customerName,
    nationalId,
    phoneNumber,
    dailyRate,
    totalLoanAmount,
    termDays,
    nextPaymentDue,
    amountPaid,
    customerPhoto,
    nationalIdFront,
    nationalIdBack,
    isStolen
  } = body;

  if (amountPaid !== undefined) {
    return errorResponse('amountPaid is ledger-controlled; record a payment through the payments endpoint', 400);
  }

  const restrictedFinancialEdit = [dailyRate, totalLoanAmount, termDays, nextPaymentDue].some((value) => value !== undefined);
  if (restrictedFinancialEdit && !canReleaseOrDeleteAccount(locals.dealer.role)) {
    return errorResponse('Only branch admins and above can change loan terms or due dates', 403);
  }

  const updates: string[] = [];
  const args: (string | number | null)[] = [];

  if (customerName !== undefined) {
    const value = String(customerName).trim();
    if (value.length < 2 || value.length > 120) return errorResponse('customerName must be between 2 and 120 characters', 400);
    updates.push('customer_name = ?'); args.push(value);
  }
  if (nationalId !== undefined) {
    const value = String(nationalId).trim().toUpperCase();
    if (value.length < 4 || value.length > 64) return errorResponse('nationalId must be between 4 and 64 characters', 400);
    updates.push('national_id = ?'); args.push(value);
  }
  if (phoneNumber !== undefined) {
    const value = String(phoneNumber).trim();
    if (!/^[0-9+()\-\s]{8,24}$/.test(value)) return errorResponse('phoneNumber must contain 8 to 24 valid phone characters', 400);
    updates.push('phone_number = ?'); args.push(value);
  }

  const addSafeInteger = (column: string, value: unknown, fieldName: string, minimum: number) => {
    if (value === undefined) return null;
    const parsed = Number(value);
    if (!Number.isSafeInteger(parsed) || parsed < minimum) return `${fieldName} must be a whole number and at least ${minimum}`;
    updates.push(`${column} = ?`); args.push(parsed);
    return null;
  };
  const numericError = addSafeInteger('daily_rate', dailyRate, 'dailyRate', 1)
    || addSafeInteger('total_loan_amount', totalLoanAmount, 'totalLoanAmount', 1)
    || addSafeInteger('term_days', termDays, 'termDays', 1)
    || addSafeInteger('next_payment_due', nextPaymentDue, 'nextPaymentDue', 1);
  if (numericError) return errorResponse(numericError, 400);

  if (isStolen !== undefined && typeof isStolen !== 'boolean') {
    return errorResponse('isStolen must be a boolean', 400);
  }
  const nowMillis = Date.now();
  const nowSeconds = Math.floor(nowMillis / 1000);
  const DAY_MS = 24 * 60 * 60 * 1000;

  if (isStolen !== undefined) {
    const stolen = Boolean(isStolen);
    updates.push('is_stolen = ?');
    args.push(stolen ? 1 : 0);
    updates.push('locked_by_dealer = ?');
    args.push(stolen ? 1 : 0);
    updates.push('next_payment_due = ?');
    args.push(stolen ? nowMillis - 60 * 60 * 1000 : nowMillis + DAY_MS);
  }

  const uploadBase64ToR2 = async (base64Data: unknown, keyPrefix: string): Promise<string | null> => {
    if (base64Data == null || base64Data === '') return null;
    if (typeof base64Data !== 'string') throw new Error('KYC image must be a Base64 string or null');

    const match = base64Data.trim().match(/^data:image\/(jpeg|jpg|png|webp);base64,([A-Za-z0-9+/=\s]+)$/i);
    const mimeToken = match?.[1]?.toLowerCase() ?? 'jpeg';
    const cleanBase64 = (match?.[2] ?? base64Data).replace(/\s/g, '');
    if (!/^[A-Za-z0-9+/]*={0,2}$/.test(cleanBase64) || cleanBase64.length % 4 === 1) {
      throw new Error('KYC image is not valid Base64 data');
    }

    const binaryString = atob(cleanBase64);
    if (binaryString.length === 0 || binaryString.length > 5 * 1024 * 1024) {
      throw new Error('KYC image must be between 1 byte and 5MB');
    }
    const bytes = new Uint8Array(binaryString.length);
    for (let index = 0; index < binaryString.length; index += 1) bytes[index] = binaryString.charCodeAt(index);

    const extension = mimeToken === 'png' ? 'png' : mimeToken === 'webp' ? 'webp' : 'jpg';
    const contentType = mimeToken === 'png' ? 'image/png' : mimeToken === 'webp' ? 'image/webp' : 'image/jpeg';
    const key = `${keyPrefix}.${extension}`;
    await getR2({ platform }).put(key, bytes, { httpMetadata: { contentType } });
    return key;
  };

  const accountId = params.id;
  try {
    if (customerPhoto !== undefined) {
      const path = await uploadBase64ToR2(customerPhoto, `kyc/customer_${accountId}_photo`);
      updates.push('customer_photo_path = ?'); args.push(path);
    }
    if (nationalIdFront !== undefined) {
      const path = await uploadBase64ToR2(nationalIdFront, `kyc/customer_${accountId}_id_front`);
      updates.push('national_id_front_path = ?'); args.push(path);
    }
    if (nationalIdBack !== undefined) {
      const path = await uploadBase64ToR2(nationalIdBack, `kyc/customer_${accountId}_id_back`);
      updates.push('national_id_back_path = ?'); args.push(path);
    }
  } catch (error) {
    console.error('KYC image update failed', error);
    return errorResponse(error instanceof Error ? error.message : 'Unable to store KYC image', 502);
  }

  if (updates.length === 0) {
    return errorResponse('No fields to update', 400);
  }

  updates.push('updated_at = ?');
  args.push(nowSeconds);
  args.push(params.id);

  await db.prepare(`UPDATE accounts SET ${updates.join(', ')} WHERE id = ?`).bind(...args).run();

  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ?
  `).bind(params.id).first();

  if (!row) return errorResponse('Account updated but could not be reloaded', 500);

  if (isStolen !== undefined) {
    const fcmToken = String(row.fcm_token ?? '').trim();
    if (fcmToken) {
      const fcmEnv = platform?.env as { FCM_SERVICE_ACCOUNT_EMAIL?: string; FCM_SERVICE_ACCOUNT_PRIVATE_KEY?: string; FCM_PROJECT_ID?: string } | undefined;
      if (fcmEnv) {
        sendFcm(
          fcmToken,
          {
            type: isStolen ? 'stolen' : 'unlock',
            accountId: params.id,
            isStolen: isStolen ? 'true' : 'false'
          },
          fcmEnv
        ).catch(() => {});
      }
    }
    await db.prepare(
      "INSERT INTO lock_events (id, account_id, event_type, triggered_by, created_at) VALUES (?, ?, ?, 'dealer', ?)"
    ).bind(uuidv4(), params.id, isStolen ? 'stolen' : 'recover', nowSeconds).run();
  }

  const nextDue = Number(row!.next_payment_due);
  const amtPaid = Number(row!.amount_paid);
  const totalLoan = Number(row!.total_loan_amount);
  const status: Status = releaseApproved(row as Record<string, unknown>)
    ? 'ACTIVE'
    : (row!.is_stolen === 1 ? 'STOLEN' : (row!.locked_by_dealer === 1 ? 'LOCKED' : computeStatus(nextDue)));

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
    nextPaymentDueEpochMillis: nextDue,
    status,
    lockedByDealer: Number(row!.locked_by_dealer ?? 0),
    isStolen: row!.is_stolen === 1,
    customerPhotoPath: row!.customer_photo_path as string | null,
    nationalIdFrontPath: row!.national_id_front_path as string | null,
    nationalIdBackPath: row!.national_id_back_path as string | null,
    termDays: Number(row!.term_days),
    downPayment: Number(row!.down_payment),
    ...releaseFields(row as Record<string, unknown>)
  };

  return json(customer);
};
export const DELETE: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (!canReleaseOrDeleteAccount(locals.dealer.role)) {
    return errorResponse('Only branch admins and above can delete customer accounts', 403);
  }

  const db = getDb({ platform });
  const scope = getAccountScopeFilter(locals.dealer, 'a');
  const row = await db.prepare(`
    SELECT a.id, a.device_id
    FROM accounts a
    WHERE a.id = ? AND ${scope.where}
  `).bind(params.id, ...scope.params).first<{ id: string; device_id: string }>();

  if (!row) return errorResponse('Account not found', 404);

  await db.batch([
    db.prepare('DELETE FROM location_logs WHERE account_id = ?').bind(params.id),
    db.prepare('DELETE FROM provisioning_tokens WHERE account_id = ?').bind(params.id),
    db.prepare('DELETE FROM payments WHERE account_id = ?').bind(params.id),
    db.prepare('DELETE FROM lock_events WHERE account_id = ?').bind(params.id),
    db.prepare('DELETE FROM accounts WHERE id = ?').bind(params.id),
    db.prepare("UPDATE devices SET status = 'in_stock' WHERE id = ?").bind(row.device_id)
  ]);

  return json({ success: true, id: params.id, deviceId: row.device_id });
};
