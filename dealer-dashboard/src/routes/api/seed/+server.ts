import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

export const POST: RequestHandler = async ({ platform }) => {
  const db = getDb({ platform });
  const dealerId = 'dealer-demo-001';

  const devices = [
    { imei: '356938035643801', model: 'Samsung Galaxy A15' },
    { imei: '356938035643802', model: 'Xiaomi Redmi 13C' },
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
  for (const row of allDevices.results) {
    deviceMap.set(row.imei as string, row.id as string);
  }

  const plans = await db.prepare('SELECT id, name FROM plans').all();
  const planMap = new Map<string, string>();
  for (const row of plans.results) {
    planMap.set(row.name as string, row.id as string);
  }

  const accounts = [
    { imei: '356938035643801', planName: 'Lite 90', customerName: 'Amani Mwangi', nationalId: '29845112', phone: '+254712345001', amountPaid: 18400, totalLoan: 27000, dailyRate: 110, daysOffset: 5 },
    { imei: '356938035643802', planName: 'Lite 90', customerName: 'Fatuma Hassan', nationalId: '31002847', phone: '+254712345002', amountPaid: 19500, totalLoan: 19500, dailyRate: 95, daysOffset: 11 },
    { imei: '356938035643803', planName: 'Standard 180', customerName: 'Brian Otieno', nationalId: '28771460', phone: '+254712345003', amountPaid: 9800, totalLoan: 22500, dailyRate: 100, daysOffset: 0 },
    { imei: '356938035643804', planName: 'Premium 365', customerName: 'Grace Wanjiru', nationalId: '30558921', phone: '+254712345004', amountPaid: 4200, totalLoan: 36000, dailyRate: 150, daysOffset: -2 },
    { imei: '356938035643805', planName: 'Lite 90', customerName: 'Joseph Kiprono', nationalId: '27410093', phone: '+254712345005', amountPaid: 12600, totalLoan: 18000, dailyRate: 90, daysOffset: 2 },
    { imei: '356938035643806', planName: 'Standard 180', customerName: 'Naliaka Wekesa', nationalId: '32099517', phone: '+254712345006', amountPaid: 22100, totalLoan: 25200, dailyRate: 120, daysOffset: 0 },
    { imei: '356938035643807', planName: 'Premium 365', customerName: 'Daniel Mutua', nationalId: '26688301', phone: '+254712345007', amountPaid: 15000, totalLoan: 45000, dailyRate: 180, daysOffset: -1 },
    { imei: '356938035643808', planName: 'Lite 90', customerName: 'Mercy Chebet', nationalId: '31774208', phone: '+254712345008', amountPaid: 6750, totalLoan: 13500, dailyRate: 70, daysOffset: 6 }
  ];

  const now = Date.now();
  const paymentMethods = ['mpesa', 'cash', 'bank_transfer'];

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
      Math.round(a.totalLoan * 0.2), 90, 'KES',
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
        `SP${100000 + i}${j + 1}`, dealerId,
        Math.floor((now - (j + 1) * 86400 * (i + 1)) / 1000)
      ).run();
    }
  }

  return json({ success: true, message: 'Database seeded with demo data' });
};