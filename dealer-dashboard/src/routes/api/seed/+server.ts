import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

export const DELETE: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);

  const db = getDb({ platform });
  const dealerId = locals.dealer.id;

  const accountIds = await db.prepare('SELECT id FROM accounts WHERE dealer_id = ?')
    .bind(dealerId).all();

  for (const row of accountIds.results) {
    const aid = row.id as string;
    await db.prepare('DELETE FROM payments WHERE account_id = ?').bind(aid).run();
    await db.prepare('DELETE FROM lock_events WHERE account_id = ?').bind(aid).run();
  }

  await db.prepare('DELETE FROM accounts WHERE dealer_id = ?').bind(dealerId).run();
  await db.prepare('UPDATE devices SET status = \'in_stock\', dealer_id = NULL WHERE dealer_id = ?').bind(dealerId).run();

  return json({ success: true, message: 'All accounts, payments, and device assignments cleared for this dealer' });
};

export const POST: RequestHandler = async ({ locals, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (platform?.env?.ALLOW_DEMO_SEED !== 'true') {
    return errorResponse('Demo seeding is disabled', 404);
  }

  const db = getDb({ platform });
  const dealerId = locals.dealer.id;

  const devices = [
    { imei: '356938035643801', model: 'Samsung Galaxy A07' },
    { imei: '356938035643802', model: 'Samsung Galaxy A15' },
    { imei: '356938035643803', model: 'Tecno Spark 20' },
    { imei: '356938035643804', model: 'Samsung Galaxy A25' },
    { imei: '356938035643805', model: 'Infinix Hot 40' },
    { imei: '356938035643806', model: 'Oppo A18' },
    { imei: '356938035643807', model: 'Tecno Camon 30' },
    { imei: '356938035643808', model: 'Samsung Galaxy A05s' }
  ];

  for (const device of devices) {
    await db.prepare(
      'INSERT OR IGNORE INTO devices (id, imei, model, dealer_id, status, created_at) VALUES (?, ?, ?, ?, ?, ?)'
    ).bind(uuidv4(), device.imei, device.model, dealerId, 'sold', Math.floor(Date.now() / 1000)).run();
  }

  const allDevices = await db.prepare('SELECT id, imei FROM devices WHERE dealer_id = ?').bind(dealerId).all();
  const deviceMap = new Map<string, string>();
  for (const row of allDevices.results) deviceMap.set(row.imei as string, row.id as string);

  const plans = await db.prepare('SELECT id, name FROM plans').all();
  const planMap = new Map<string, string>();
  for (const row of plans.results) planMap.set(row.name as string, row.id as string);

  // All amounts are integer pesewas, not floating-point cedis.
  const accounts = [
    { imei: '356938035643801', planName: 'Lite 90', customerName: 'Kwame Mensah', nationalId: 'GHA-100000001-1', phone: '+233201234501', amountPaid: 184000, totalLoan: 270000, dailyRate: 1100, daysOffset: 5 },
    { imei: '356938035643802', planName: 'Lite 90', customerName: 'Ama Boateng', nationalId: 'GHA-100000002-2', phone: '+233241234502', amountPaid: 195000, totalLoan: 195000, dailyRate: 950, daysOffset: 11 },
    { imei: '356938035643803', planName: 'Standard 180', customerName: 'Kofi Asare', nationalId: 'GHA-100000003-3', phone: '+233501234503', amountPaid: 98000, totalLoan: 225000, dailyRate: 1000, daysOffset: 0 },
    { imei: '356938035643804', planName: 'Premium 365', customerName: 'Akosua Owusu', nationalId: 'GHA-100000004-4', phone: '+233541234504', amountPaid: 42000, totalLoan: 360000, dailyRate: 1500, daysOffset: -2 },
    { imei: '356938035643805', planName: 'Lite 90', customerName: 'Yaw Osei', nationalId: 'GHA-100000005-5', phone: '+233551234505', amountPaid: 126000, totalLoan: 180000, dailyRate: 900, daysOffset: 2 },
    { imei: '356938035643806', planName: 'Standard 180', customerName: 'Abena Addo', nationalId: 'GHA-100000006-6', phone: '+233271234506', amountPaid: 221000, totalLoan: 252000, dailyRate: 1200, daysOffset: 0 },
    { imei: '356938035643807', planName: 'Premium 365', customerName: 'Kojo Agyeman', nationalId: 'GHA-100000007-7', phone: '+233591234507', amountPaid: 150000, totalLoan: 450000, dailyRate: 1800, daysOffset: -1 },
    { imei: '356938035643808', planName: 'Lite 90', customerName: 'Adwoa Frimpong', nationalId: 'GHA-100000008-8', phone: '+233261234508', amountPaid: 67500, totalLoan: 135000, dailyRate: 700, daysOffset: 6 }
  ];

  const now = Date.now();
  const paymentMethods = ['mobile_money', 'cash', 'bank'];

  for (let i = 0; i < accounts.length; i++) {
    const a = accounts[i];
    const deviceId = deviceMap.get(a.imei);
    const planId = planMap.get(a.planName);
    if (!deviceId || !planId) continue;

    const accountId = `ACC-${100000 + i}`;
    const nextPaymentDue = now + a.daysOffset * DAY_MS + 3 * HOUR_MS;

    await db.prepare(
      `INSERT OR IGNORE INTO accounts (id, customer_name, national_id, phone_number, device_id, dealer_id, plan_id, total_loan_amount, amount_paid, daily_rate, next_payment_due, status, locked_by_dealer, down_payment, term_days, currency_code, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(
      accountId, a.customerName, a.nationalId, a.phone, deviceId, dealerId, planId,
      a.totalLoan, a.amountPaid, a.dailyRate, nextPaymentDue, 'ACTIVE', 0,
      Math.round(a.totalLoan * 0.2), 90, 'GHS',
      Math.floor(now / 1000) - (8 - i) * 86400,
      Math.floor(now / 1000)
    ).run();

    for (let j = 0; j < 3 && j * 500 < a.amountPaid; j++) {
      const amount = Math.min(Math.round(a.amountPaid / 3), a.amountPaid);
      if (amount <= 0) continue;
      await db.prepare(
        `INSERT OR IGNORE INTO payments (id, account_id, amount, method, reference, recorded_by, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)`
      ).bind(
        uuidv4(), accountId, amount, paymentMethods[j % 3],
        `TB${100000 + i}${j + 1}`, dealerId,
        Math.floor((now - (j + 1) * 86400 * (i + 1)) / 1000)
      ).run();
    }
  }

  return json({ success: true, message: 'Database seeded with Ghana demo data' });
};
