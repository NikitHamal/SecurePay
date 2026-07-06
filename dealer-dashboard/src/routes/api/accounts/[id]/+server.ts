import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeStatus, errorResponse, releaseFields, releaseApproved, getR2 } from '$lib/api/server';
import { sendFcm } from '$lib/api/fcm';
import type { Customer, Status } from '$lib/types';
import Map from '$lib/components/ui/Map.svelte';

export const GET: RequestHandler = async ({ locals, params, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const db = getDb({ platform });
  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ? AND a.dealer_id = ?
  `).bind(params.id, locals.dealer.id).first();

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

  const updates: string[] = [];
  const args: (string | number)[] = [];

  if (customerName !== undefined) { updates.push('customer_name = ?'); args.push(customerName); }
  if (nationalId !== undefined) { updates.push('national_id = ?'); args.push(nationalId); }
  if (phoneNumber !== undefined) { updates.push('phone_number = ?'); args.push(phoneNumber); }
  if (dailyRate !== undefined) { updates.push('daily_rate = ?'); args.push(dailyRate); }
  if (totalLoanAmount !== undefined) { updates.push('total_loan_amount = ?'); args.push(totalLoanAmount); }
  if (termDays !== undefined) { updates.push('term_days = ?'); args.push(termDays); }
  if (nextPaymentDue !== undefined) { updates.push('next_payment_due = ?'); args.push(nextPaymentDue); }
  if (amountPaid !== undefined) { updates.push('amount_paid = ?'); args.push(amountPaid); }
  if (isStolen !== undefined) {
    updates.push('is_stolen = ?');
    args.push(isStolen ? 1 : 0);
    if (isStolen) {
      // A stolen device should lock on the next heartbeat even if the dealer did
      // not separately press Force Lock. Unflagging does not auto-unlock; the
      // dealer can use Force Unlock after recovery/verification.
      updates.push('locked_by_dealer = ?');
      args.push(1);
    }
  }

  // Helper to decode Base64 and upload to R2
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

  const accountId = params.id;

  if (customerPhoto !== undefined) {
    const customerPhotoPath = customerPhoto ? await uploadBase64ToR2(customerPhoto, `kyc/customer_${accountId}_photo.jpg`) : null;
    updates.push('customer_photo_path = ?');
    args.push(customerPhotoPath || '');
  }
  if (nationalIdFront !== undefined) {
    const nationalIdFrontPath = nationalIdFront ? await uploadBase64ToR2(nationalIdFront, `kyc/customer_${accountId}_id_front.jpg`) : null;
    updates.push('national_id_front_path = ?');
    args.push(nationalIdFrontPath || '');
  }
  if (nationalIdBack !== undefined) {
    const nationalIdBackPath = nationalIdBack ? await uploadBase64ToR2(nationalIdBack, `kyc/customer_${accountId}_id_back.jpg`) : null;
    updates.push('national_id_back_path = ?');
    args.push(nationalIdBackPath || '');
  }

  if (updates.length === 0) {
    return errorResponse('No fields to update', 400);
  }

  updates.push('updated_at = ?');
  args.push(Math.floor(Date.now() / 1000));
  args.push(params.id);
  args.push(locals.dealer.id);

  const db = getDb({ platform });
  await db.prepare(`UPDATE accounts SET ${updates.join(', ')} WHERE id = ? AND dealer_id = ?`).bind(...args).run();

  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ? AND a.dealer_id = ?
  `).bind(params.id, locals.dealer.id).first();

  if (isStolen !== undefined && row) {
    const fcmToken = String(row.fcm_token ?? '').trim();
    if (fcmToken) {
      const fcmEnv = platform?.env as { FCM_SERVICE_ACCOUNT_EMAIL?: string; FCM_SERVICE_ACCOUNT_PRIVATE_KEY?: string; FCM_PROJECT_ID?: string } | undefined;
      if (fcmEnv) {
        sendFcm(fcmToken, { type: isStolen ? 'lock' : 'unlock', accountId: params.id }, fcmEnv).catch(() => {});
      }
    }
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
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const db = getDb({ platform });
  const row = await db.prepare(`
    SELECT id, device_id
    FROM accounts
    WHERE id = ? AND dealer_id = ?
  `).bind(params.id, locals.dealer.id).first<{ id: string; device_id: string }>();

  if (!row) {
    return errorResponse('Account not found', 404);
  }

  await db.batch([
    db.prepare('DELETE FROM location_logs WHERE account_id = ?').bind(params.id),
    db.prepare('DELETE FROM provisioning_tokens WHERE account_id = ?').bind(params.id),
    db.prepare('DELETE FROM payments WHERE account_id = ?').bind(params.id),
    db.prepare('DELETE FROM lock_events WHERE account_id = ?').bind(params.id),
    db.prepare('DELETE FROM accounts WHERE id = ? AND dealer_id = ?').bind(params.id, locals.dealer.id),
    db.prepare('UPDATE devices SET status = ? WHERE id = ? AND dealer_id = ?').bind('in_stock', row.device_id, locals.dealer.id)
  ]);

  return json({ success: true, id: params.id, deviceId: row.device_id });
};
