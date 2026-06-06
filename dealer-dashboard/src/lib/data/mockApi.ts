import {
  evaluateStatus,
  type CustomerNode,
  type DeviceStatus
} from '$lib/types';

export interface KpiSummary {
  activeNodes: number;
  lockedNodes: number;
  warningNodes: number;
  totalFinanced: number;
  totalCollected: number;
  totalOutstanding: number;
  defaultCount: number;
}

const HOUR_MS = 3_600_000;
const DAY_MS = 86_400_000;

/**
 * Simulated network latency so the UI exercises its async/loading paths.
 */
function delay<T>(value: T, ms = 220): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms));
}

/**
 * Build the seed dataset relative to "now" so the ACTIVE / WARNING / LOCKED
 * mix is realistic every time the app boots.
 */
function buildSeed(): CustomerNode[] {
  const now = Date.now();

  const seeds: Array<Omit<CustomerNode, 'status'>> = [
    {
      customerId: 'CUST-1001',
      customerName: 'Amina Wanjiru',
      deviceModel: 'Samsung Galaxy A15',
      imei: '356938035643809',
      totalLoanAmount: 24000,
      amountPaid: 18600,
      remainingBalance: 5400,
      dailyInstallment: 120,
      serverEpochMillis: now,
      nextDueEpochMillis: now + 6 * DAY_MS // ACTIVE
    },
    {
      customerId: 'CUST-1002',
      customerName: 'Brian Otieno',
      deviceModel: 'Tecno Spark 20',
      imei: '356938035643810',
      totalLoanAmount: 18000,
      amountPaid: 6300,
      remainingBalance: 11700,
      dailyInstallment: 90,
      serverEpochMillis: now,
      nextDueEpochMillis: now + 18 * HOUR_MS // WARNING (within a day)
    },
    {
      customerId: 'CUST-1003',
      customerName: 'Cynthia Achieng',
      deviceModel: 'Xiaomi Redmi 13C',
      imei: '356938035643811',
      totalLoanAmount: 21000,
      amountPaid: 21000,
      remainingBalance: 0,
      dailyInstallment: 105,
      serverEpochMillis: now,
      nextDueEpochMillis: now + 12 * DAY_MS // ACTIVE (almost paid off)
    },
    {
      customerId: 'CUST-1004',
      customerName: 'David Mwangi',
      deviceModel: 'Infinix Hot 40',
      imei: '356938035643812',
      totalLoanAmount: 16500,
      amountPaid: 4950,
      remainingBalance: 11550,
      dailyInstallment: 75,
      serverEpochMillis: now,
      nextDueEpochMillis: now - 4 * HOUR_MS // LOCKED (overdue)
    },
    {
      customerId: 'CUST-1005',
      customerName: 'Esther Njeri',
      deviceModel: 'Samsung Galaxy A05s',
      imei: '356938035643813',
      totalLoanAmount: 19500,
      amountPaid: 13650,
      remainingBalance: 5850,
      dailyInstallment: 95,
      serverEpochMillis: now,
      nextDueEpochMillis: now + 3 * HOUR_MS // WARNING
    },
    {
      customerId: 'CUST-1006',
      customerName: 'Felix Kiprop',
      deviceModel: 'Oppo A18',
      imei: '356938035643814',
      totalLoanAmount: 22500,
      amountPaid: 9000,
      remainingBalance: 13500,
      dailyInstallment: 110,
      serverEpochMillis: now,
      nextDueEpochMillis: now - 30 * HOUR_MS // LOCKED (overdue)
    },
    {
      customerId: 'CUST-1007',
      customerName: 'Grace Auma',
      deviceModel: 'Tecno Camon 30',
      imei: '356938035643815',
      totalLoanAmount: 27000,
      amountPaid: 21600,
      remainingBalance: 5400,
      dailyInstallment: 135,
      serverEpochMillis: now,
      nextDueEpochMillis: now + 9 * DAY_MS // ACTIVE
    },
    {
      customerId: 'CUST-1008',
      customerName: 'Hassan Abdi',
      deviceModel: 'Xiaomi Poco C65',
      imei: '356938035643816',
      totalLoanAmount: 17400,
      amountPaid: 8700,
      remainingBalance: 8700,
      dailyInstallment: 87,
      serverEpochMillis: now,
      nextDueEpochMillis: now + 20 * HOUR_MS // WARNING
    }
  ];

  return seeds.map((seed) => ({
    ...seed,
    status: evaluateStatus(now, seed.nextDueEpochMillis)
  }));
}

// In-memory store so action mutations persist for the session.
let nodes: CustomerNode[] = buildSeed();

function clone(node: CustomerNode): CustomerNode {
  return { ...node };
}

function findIndexOrThrow(customerId: string): number {
  const index = nodes.findIndex((n) => n.customerId === customerId);
  if (index === -1) {
    throw new Error(`Unknown customerId: ${customerId}`);
  }
  return index;
}

/**
 * Fetch all device nodes, re-evaluating status against the current time.
 */
export function getNodes(): Promise<CustomerNode[]> {
  const now = Date.now();
  nodes = nodes.map((n) => ({
    ...n,
    serverEpochMillis: now,
    status: evaluateStatus(now, n.nextDueEpochMillis)
  }));
  return delay(nodes.map(clone));
}

/**
 * Push a device's next-due time forward by `hours`, recompute status, persist.
 */
export function extendTimer(customerId: string, hours: number): Promise<CustomerNode> {
  const index = findIndexOrThrow(customerId);
  const now = Date.now();
  // Extend from whichever is later: now or the existing due time, so extending
  // an overdue device always grants a fresh future window.
  const base = Math.max(now, nodes[index].nextDueEpochMillis);
  const nextDue = base + hours * HOUR_MS;
  const updated: CustomerNode = {
    ...nodes[index],
    serverEpochMillis: now,
    nextDueEpochMillis: nextDue,
    status: evaluateStatus(now, nextDue)
  };
  nodes[index] = updated;
  return delay(clone(updated));
}

/**
 * Force a remote lock: set status LOCKED and next-due to now.
 */
export function forceRemoteLock(customerId: string): Promise<CustomerNode> {
  const index = findIndexOrThrow(customerId);
  const now = Date.now();
  const updated: CustomerNode = {
    ...nodes[index],
    serverEpochMillis: now,
    nextDueEpochMillis: now,
    status: 'LOCKED' as DeviceStatus
  };
  nodes[index] = updated;
  return delay(clone(updated));
}

/**
 * Derive the KPI summary from a set of nodes. A "default" is a LOCKED device
 * that still carries an outstanding balance.
 */
export function getKpis(input: CustomerNode[]): KpiSummary {
  const summary: KpiSummary = {
    activeNodes: 0,
    lockedNodes: 0,
    warningNodes: 0,
    totalFinanced: 0,
    totalCollected: 0,
    totalOutstanding: 0,
    defaultCount: 0
  };

  for (const node of input) {
    if (node.status === 'ACTIVE') summary.activeNodes += 1;
    else if (node.status === 'WARNING') summary.warningNodes += 1;
    else if (node.status === 'LOCKED') summary.lockedNodes += 1;

    summary.totalFinanced += node.totalLoanAmount;
    summary.totalCollected += node.amountPaid;
    summary.totalOutstanding += node.remainingBalance;

    if (node.status === 'LOCKED' && node.remainingBalance > 0) {
      summary.defaultCount += 1;
    }
  }

  return summary;
}
