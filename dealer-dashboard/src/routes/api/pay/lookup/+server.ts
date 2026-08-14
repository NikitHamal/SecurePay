import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { errorResponse, getDb } from '$lib/api/server';

export const GET: RequestHandler = async ({ url, platform }) => {
  const query = (url.searchParams.get('account') || url.searchParams.get('acc') || url.searchParams.get('phone') || '').trim();
  if (!query) {
    return errorResponse('Account number or phone number is required', 400);
  }

  const db = getDb({ platform });

  // Look up by customer_account_number or phone_number
  const cleanPhone = query.replace(/\D/g, '');
  const phoneVariants = [
    query,
    cleanPhone,
    cleanPhone.startsWith('233') ? '0' + cleanPhone.slice(3) : cleanPhone,
    cleanPhone.startsWith('0') ? '233' + cleanPhone.slice(1) : cleanPhone,
    cleanPhone.startsWith('0') ? '+233' + cleanPhone.slice(1) : cleanPhone
  ].filter(Boolean);

  const placeholders = phoneVariants.map(() => '?').join(',');

  const row = await db.prepare(`
    SELECT a.id, a.customer_name, a.customer_account_number, a.phone_number,
           a.total_loan_amount, a.amount_paid, a.daily_rate, a.next_payment_due,
           a.status, a.dealer_id, d.model as device_model, d.imei
      FROM accounts a
      LEFT JOIN devices d ON d.id = a.device_id
     WHERE a.customer_account_number = ?
        OR a.phone_number IN (${placeholders})
     LIMIT 1
  `).bind(query, ...phoneVariants).first<Record<string, any>>();

  if (!row) {
    return errorResponse('No active TouchBase account found for this account number or phone.', 404);
  }

  // Mask name for privacy (e.g. "Daniel Sem" -> "Daniel S.")
  const nameParts = String(row.customer_name || 'Customer').trim().split(/\s+/);
  const maskedName = nameParts.length > 1
    ? `${nameParts[0]} ${nameParts[1][0]}.`
    : nameParts[0];

  const totalLoan = Number(row.total_loan_amount) || 0;
  const amountPaid = Number(row.amount_paid) || 0;
  const remaining = Math.max(0, totalLoan - amountPaid);
  const dailyRate = Number(row.daily_rate) || 0;

  return json({
    id: row.id,
    customerName: maskedName,
    accountNumber: row.customer_account_number || row.phone_number,
    phone: row.phone_number,
    deviceModel: row.device_model || 'Smartphone',
    totalLoanAmount: totalLoan,
    amountPaid,
    remainingBalance: remaining,
    dailyRate,
    nextPaymentDue: Number(row.next_payment_due) || 0,
    status: row.status || 'ACTIVE'
  });
};
