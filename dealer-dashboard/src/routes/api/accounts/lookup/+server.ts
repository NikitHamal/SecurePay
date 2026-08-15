import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb } from '$lib/api/server';

export const POST: RequestHandler = async ({ request, platform }) => {
  const body = await request.json().catch(() => ({})) as Record<string, unknown>;
  const query = String(body.account_number ?? body.accountNumber ?? body.phone ?? '').trim();

  if (!query) {
    return json({ ok: false, message: 'Enter your TouchBase account number.' }, { status: 400 });
  }

  const db = getDb({ platform });

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
    return json({ ok: false, message: 'Account not found. Please verify your account number.' }, { status: 404 });
  }

  const totalLoanPesewas = Number(row.total_loan_amount) || 0;
  const amountPaidPesewas = Number(row.amount_paid) || 0;
  const remainingPesewas = Math.max(0, totalLoanPesewas - amountPaidPesewas);
  const dailyRatePesewas = Number(row.daily_rate) || 0;

  return json({
    ok: true,
    account: {
      id: row.id,
      customer_account_number: row.customer_account_number || row.phone_number || query,
      customer_name: row.customer_name || 'Customer',
      phone_number: row.phone_number || '',
      total_loan_amount: totalLoanPesewas / 100,
      amount_paid: amountPaidPesewas / 100,
      remaining_balance: remainingPesewas / 100,
      daily_rate: dailyRatePesewas / 100,
      next_payment_due: Number(row.next_payment_due) || 0,
      status: row.status || 'ACTIVE',
      device_model: row.device_model || 'Smartphone'
    }
  });
};

export const GET: RequestHandler = async ({ url, platform }) => {
  const query = (url.searchParams.get('account_number') || url.searchParams.get('account') || url.searchParams.get('phone') || '').trim();
  if (!query) {
    return json({ ok: false, message: 'Account number is required.' }, { status: 400 });
  }

  const db = getDb({ platform });
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
    return json({ ok: false, message: 'Account not found.' }, { status: 404 });
  }

  const totalLoanPesewas = Number(row.total_loan_amount) || 0;
  const amountPaidPesewas = Number(row.amount_paid) || 0;
  const remainingPesewas = Math.max(0, totalLoanPesewas - amountPaidPesewas);

  return json({
    ok: true,
    account: {
      id: row.id,
      customer_account_number: row.customer_account_number || row.phone_number || query,
      customer_name: row.customer_name || 'Customer',
      phone_number: row.phone_number || '',
      total_loan_amount: totalLoanPesewas / 100,
      amount_paid: amountPaidPesewas / 100,
      remaining_balance: remainingPesewas / 100,
      daily_rate: (Number(row.daily_rate) || 0) / 100,
      next_payment_due: Number(row.next_payment_due) || 0,
      status: row.status || 'ACTIVE',
      device_model: row.device_model || 'Smartphone'
    }
  });
};
