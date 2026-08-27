import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, computeAccountStatus, errorResponse, releaseFields } from '$lib/api/server';
import { parsePaymentMethod, paymentMethodStorageValue } from '$lib/payment-method';
import { applyPayment } from '$lib/payments';
import type { Customer, Status } from '$lib/types';
import { getAccountScopeFilter } from '$lib/auth';
import { logActivity } from '$lib/audit';

export const POST: RequestHandler = async ({ locals, request, platform }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const body = await request.json();
  const accountId = String(body.accountId ?? '').trim();
  const amount = Number(body.amount);
  const method = parsePaymentMethod(body.method);
  const reference = typeof body.reference === 'string' ? body.reference.trim().slice(0, 120) : '';

  if (!accountId || !Number.isSafeInteger(amount) || amount <= 0 || !method) {
    return errorResponse('A valid accountId, positive integer amount in pesewas, and payment method are required', 400);
  }

  const db = getDb({ platform });
  const scope = getAccountScopeFilter(locals.dealer, 'a');
  const account = await db.prepare(`SELECT a.* FROM accounts a WHERE a.id = ? AND ${scope.where}`)
    .bind(accountId, ...scope.params)
    .first();

  if (!account) {
    return errorResponse('Account not found', 404);
  }

  const totalLoan = Number(account.total_loan_amount);
  const dailyRate = Number(account.daily_rate);

  let paymentId: string;
  try {
    ({ paymentId } = await applyPayment({
      db,
      accountId,
      amount,
      method: paymentMethodStorageValue(method),
      reference,
      recordedBy: locals.dealer.id,
      env: platform?.env
    }));
  } catch (error) {
    const message = String(error);
    if (message.includes('exceeds remaining')) {
      return errorResponse('Payment exceeds the remaining loan balance', 409);
    }
    if (message.includes('daily rate')) {
      return errorResponse('Account daily rate is invalid', 409);
    }
    return errorResponse('Payment could not be recorded', 409);
  }

  const row = await db.prepare(`
    SELECT a.*, d.imei, d.model as device_model, COALESCE(p.name, 'Custom') as plan_name
    FROM accounts a
    JOIN devices d ON a.device_id = d.id
    LEFT JOIN plans p ON a.plan_id = p.id
    WHERE a.id = ?
  `).bind(accountId).first();

  if (!row) {
    return errorResponse('Payment recorded but the account could not be reloaded', 500);
  }

  await logActivity(db, {
    actor: locals.dealer,
    action: 'PAYMENT_RECORDED',
    details: `Recorded GH₵ ${(amount / 100).toLocaleString('en-GH', { minimumFractionDigits: 2 })} via ${method}`,
    customerName: String(row.customer_name),
    accountId,
    imei: String(row.imei)
  });

  const nextDue = Number(row.next_payment_due);
  const amtPaid = Number(row.amount_paid);
  const status: Status = computeAccountStatus(row as Record<string, unknown>);

  const customer: Customer = {
    id: row.id as string,
    customerName: row.customer_name as string,
    nationalId: row.national_id as string,
    phoneNumber: row.phone_number as string,
    imei: row.imei as string,
    deviceModel: row.device_model as string,
    planName: row.plan_name as string,
    totalLoanAmount: totalLoan,
    amountPaid: amtPaid,
    remainingBalance: Math.max(0, totalLoan - amtPaid),
    dailyRate,
    nextPaymentDueEpochMillis: nextDue,
    status,
    termDays: Number(row.term_days),
    downPayment: Number(row.down_payment),
    ...releaseFields(row as Record<string, unknown>)
  };

  return json({
    payment: { id: paymentId, accountId, amount, method, reference: reference || null },
    account: customer
  });
};
