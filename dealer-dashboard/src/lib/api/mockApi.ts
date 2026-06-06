import type { Customer, KpiSummary, LedgerEntry, PaymentMethod, Status } from '$lib/types';

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

/** Simulate network latency for a realistic async feel. */
function delay<T>(value: T, ms = 220): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms));
}

/**
 * Status rule (shared across the ecosystem):
 *   LOCKED   if now >= nextPaymentDue
 *   WARNING  if due within the next 24h
 *   ACTIVE   otherwise
 */
export function computeStatus(nextPaymentDueEpochMillis: number, now = Date.now()): Status {
  if (now >= nextPaymentDueEpochMillis) {
    return 'LOCKED';
  }
  if (nextPaymentDueEpochMillis - now <= DAY_MS) {
    return 'WARNING';
  }
  return 'ACTIVE';
}

/** In-memory dataset. Mutated by extendTimer / forceRemoteLock. */
const dataset: Customer[] = buildSeed();

function buildSeed(): Customer[] {
  const now = Date.now();

  const rows: Array<Omit<Customer, 'status' | 'remainingBalance'>> = [
    {
      id: 'CUS-1001',
      customerName: 'Amani Mwangi',
      nationalId: '29845112',
      phoneNumber: '+254712345001',
      imei: '356938035643801',
      deviceModel: 'Samsung Galaxy A15',
      planName: 'Daily 90',
      totalLoanAmount: 27000,
      amountPaid: 18400,
      dailyRate: 110,
      nextPaymentDueEpochMillis: now + 5 * DAY_MS + 3 * HOUR_MS
    },
    {
      id: 'CUS-1002',
      customerName: 'Fatuma Hassan',
      nationalId: '31002847',
      phoneNumber: '+254712345002',
      imei: '356938035643802',
      deviceModel: 'Xiaomi Redmi 13C',
      planName: 'Daily 60',
      totalLoanAmount: 19500,
      amountPaid: 19500,
      dailyRate: 95,
      nextPaymentDueEpochMillis: now + 11 * DAY_MS
    },
    {
      id: 'CUS-1003',
      customerName: 'Brian Otieno',
      nationalId: '28771460',
      phoneNumber: '+254712345003',
      imei: '356938035643803',
      deviceModel: 'Tecno Spark 20',
      planName: 'Daily 75',
      totalLoanAmount: 22500,
      amountPaid: 9800,
      dailyRate: 100,
      nextPaymentDueEpochMillis: now + 8 * HOUR_MS
    },
    {
      id: 'CUS-1004',
      customerName: 'Grace Wanjiru',
      nationalId: '30558921',
      phoneNumber: '+254712345004',
      imei: '356938035643804',
      deviceModel: 'Samsung Galaxy A25',
      planName: 'Daily 120',
      totalLoanAmount: 36000,
      amountPaid: 4200,
      dailyRate: 150,
      nextPaymentDueEpochMillis: now - 2 * DAY_MS
    },
    {
      id: 'CUS-1005',
      customerName: 'Joseph Kiprono',
      nationalId: '27410093',
      phoneNumber: '+254712345005',
      imei: '356938035643805',
      deviceModel: 'Infinix Hot 40',
      planName: 'Daily 60',
      totalLoanAmount: 18000,
      amountPaid: 12600,
      dailyRate: 90,
      nextPaymentDueEpochMillis: now + 2 * DAY_MS + 6 * HOUR_MS
    },
    {
      id: 'CUS-1006',
      customerName: 'Naliaka Wekesa',
      nationalId: '32099517',
      phoneNumber: '+254712345006',
      imei: '356938035643806',
      deviceModel: 'Oppo A18',
      planName: 'Daily 90',
      totalLoanAmount: 25200,
      amountPaid: 22100,
      dailyRate: 120,
      nextPaymentDueEpochMillis: now + 18 * HOUR_MS
    },
    {
      id: 'CUS-1007',
      customerName: 'Daniel Mutua',
      nationalId: '26688301',
      phoneNumber: '+254712345007',
      imei: '356938035643807',
      deviceModel: 'Tecno Camon 30',
      planName: 'Daily 150',
      totalLoanAmount: 45000,
      amountPaid: 15000,
      dailyRate: 180,
      nextPaymentDueEpochMillis: now - 6 * HOUR_MS
    },
    {
      id: 'CUS-1008',
      customerName: 'Mercy Chebet',
      nationalId: '31774208',
      phoneNumber: '+254712345008',
      imei: '356938035643808',
      deviceModel: 'Samsung Galaxy A05s',
      planName: 'Daily 45',
      totalLoanAmount: 13500,
      amountPaid: 6750,
      dailyRate: 70,
      nextPaymentDueEpochMillis: now + 6 * DAY_MS + 12 * HOUR_MS
    }
  ];

  return rows.map((row) => hydrate(row));
}

/** Compute the derived fields (remainingBalance, status) for a base row. */
function hydrate(row: Omit<Customer, 'status' | 'remainingBalance'>): Customer {
  const remainingBalance = Math.max(0, row.totalLoanAmount - row.amountPaid);
  return {
    ...row,
    remainingBalance,
    status: computeStatus(row.nextPaymentDueEpochMillis)
  };
}

function findOrThrow(id: string): Customer {
  const customer = dataset.find((c) => c.id === id);
  if (!customer) {
    throw new Error(`Customer not found: ${id}`);
  }
  return customer;
}

/** Recompute the status for every account based on the current clock. */
function refreshStatuses(): void {
  const now = Date.now();
  for (const customer of dataset) {
    customer.status = computeStatus(customer.nextPaymentDueEpochMillis, now);
  }
}

/** Return a defensive copy of all accounts (statuses refreshed). */
export function listCustomers(): Promise<Customer[]> {
  refreshStatuses();
  return delay(dataset.map((c) => ({ ...c })));
}

/** Aggregate portfolio KPIs. */
export function getKpis(): Promise<KpiSummary> {
  refreshStatuses();
  const summary: KpiSummary = {
    activeNodes: dataset.filter((c) => c.status === 'ACTIVE').length,
    lockedCount: dataset.filter((c) => c.status === 'LOCKED').length,
    warningCount: dataset.filter((c) => c.status === 'WARNING').length,
    totalOutstanding: dataset.reduce((sum, c) => sum + c.remainingBalance, 0),
    collectedToday: estimateCollectedToday()
  };
  return delay(summary);
}

/**
 * Push the next payment due date forward by `hours` and recompute the status.
 * Returns the updated account.
 */
export function extendTimer(id: string, hours: number): Promise<Customer> {
  const customer = findOrThrow(id);
  const base = Math.max(customer.nextPaymentDueEpochMillis, Date.now());
  customer.nextPaymentDueEpochMillis = base + hours * HOUR_MS;
  customer.status = computeStatus(customer.nextPaymentDueEpochMillis);
  return delay({ ...customer });
}

/**
 * Force a remote lock: set status to LOCKED and move the due date into the
 * past so the lock is consistent with the shared status rule.
 */
export function forceRemoteLock(id: string): Promise<Customer> {
  const customer = findOrThrow(id);
  customer.nextPaymentDueEpochMillis = Date.now() - HOUR_MS;
  customer.status = computeStatus(customer.nextPaymentDueEpochMillis);
  return delay({ ...customer });
}

const LEDGER_METHODS: PaymentMethod[] = ['M-PESA', 'CARD', 'BANK', 'CASH'];

/**
 * Synthesize a payment ledger from the customer accounts. Each account's
 * total paid is split into a deterministic set of installments.
 */
export function listLedger(): Promise<LedgerEntry[]> {
  const now = Date.now();
  const entries: LedgerEntry[] = [];

  dataset.forEach((customer, customerIndex) => {
    const installments = customer.amountPaid > 0 ? 3 : 0;
    for (let i = 0; i < installments; i += 1) {
      const fraction = i === installments - 1 ? 1 - (installments - 1) / installments : 1 / installments;
      const amount = Math.round(customer.amountPaid * fraction);
      if (amount <= 0) {
        continue;
      }
      const daysAgo = (customerIndex % 4) + i * 2 + 1;
      entries.push({
        id: `${customer.id}-PMT-${i + 1}`,
        customerId: customer.id,
        customerName: customer.customerName,
        imei: customer.imei,
        amount,
        dateEpochMillis: now - daysAgo * DAY_MS - customerIndex * HOUR_MS,
        method: LEDGER_METHODS[(customerIndex + i) % LEDGER_METHODS.length],
        reference: `SP${customer.id.replace('CUS-', '')}${i + 1}${customerIndex}`
      });
    }
  });

  entries.sort((a, b) => b.dateEpochMillis - a.dateEpochMillis);
  return delay(entries);
}

/** Rough estimate of collections recorded today, in KES. */
function estimateCollectedToday(): number {
  return dataset.reduce((sum, c) => {
    if (c.status === 'LOCKED') {
      return sum;
    }
    return sum + c.dailyRate;
  }, 0);
}
