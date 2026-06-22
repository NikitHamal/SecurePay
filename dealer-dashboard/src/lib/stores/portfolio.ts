import { derived, type Readable } from 'svelte/store';
import type { Customer, KpiSummary, LedgerEntry, Status } from '$lib/types';
import { customers } from './customers';
import { payments } from './payments';

export interface PortfolioMetrics {
  totalAccounts: number;
  statusBreakdown: { status: Status; count: number; percent: number }[];
  paidRatio: number;
  totalLoaned: number;
  totalPaid: number;
  totalOutstanding: number;
  avgDailyRate: number;
  models: { name: string; count: number; outstanding: number }[];
  deviceDistribution: { name: string; value: number; color: string }[];
  collectionSeries: { label: string; value: number }[];
  methodSeries: { label: string; value: number; color: string }[];
  upcoming: { id: string; name: string; phone: string; ms: number; amount: number; status: Status }[];
  health: number;
}

const FALLBACK_PALETTE = ['#10B981', '#38BDF8', '#A78BFA', '#F59E0B', '#F472B6', '#34D399', '#FBBF24', '#60A5FA'];

const METHOD_COLORS: Record<string, string> = {
  'MOBILE_MONEY': '#10B981',
  'CARD': '#F59E0B',
  'BANK': '#38BDF8',
  'CASH': '#A78BFA'
};

const METHOD_LABELS: Record<string, string> = {
  'MOBILE_MONEY': 'Mobile Money',
  'CARD': 'Card',
  'BANK': 'Bank',
  'CASH': 'Cash'
};

function statusBreakdown(list: Customer[]): PortfolioMetrics['statusBreakdown'] {
  const total = list.length || 1;
  const counts: Record<Status, number> = { ACTIVE: 0, WARNING: 0, LOCKED: 0 };
  for (const c of list) counts[c.status] += 1;
  return (['ACTIVE', 'WARNING', 'LOCKED'] as Status[]).map((s) => ({
    status: s,
    count: counts[s],
    percent: (counts[s] / total) * 100
  }));
}

function deviceDistribution(list: Customer[]): PortfolioMetrics['deviceDistribution'] {
  const map = new Map<string, number>();
  for (const c of list) map.set(c.deviceModel, (map.get(c.deviceModel) ?? 0) + 1);
  const entries = [...map.entries()].sort((a, b) => b[1] - a[1]);
  return entries.map(([name, value], i) => ({
    name,
    value,
    color: FALLBACK_PALETTE[i % FALLBACK_PALETTE.length]
  }));
}

function paidRatio(list: Customer[]): number {
  const total = list.reduce((s, c) => s + c.totalLoanAmount, 0);
  if (total === 0) return 0;
  return (list.reduce((s, c) => s + c.amountPaid, 0) / total) * 100;
}

function healthScore(list: Customer[]): number {
  if (list.length === 0) return 0;
  const activeWeight = 1.0;
  const warningWeight = 0.55;
  const lockedWeight = 0.15;
  const ratios = list.reduce(
    (acc, c) => {
      acc[c.status] += 1;
      return acc;
    },
    { ACTIVE: 0, WARNING: 0, LOCKED: 0 }
  );
  const total = list.length;
  const statusScore =
    ((ratios.ACTIVE * activeWeight + ratios.WARNING * warningWeight + ratios.LOCKED * lockedWeight) /
      total) *
    100;
  const ratioScore = paidRatio(list);
  return Math.round(statusScore * 0.65 + ratioScore * 0.35);
}

function collectionSeriesFromPayments(entries: LedgerEntry[]): PortfolioMetrics['collectionSeries'] {
  if (entries.length === 0) return [];
  const sorted = [...entries].sort((a, b) => a.dateEpochMillis - b.dateEpochMillis);
  const map = new Map<string, number>();
  for (const e of sorted) {
    const d = new Date(e.dateEpochMillis);
    const key = d.toLocaleDateString('en-GH', { day: '2-digit', month: 'short' });
    map.set(key, (map.get(key) ?? 0) + e.amount);
  }
  return [...map.entries()].map(([label, value]) => ({ label, value }));
}

function methodSeriesFromPayments(entries: LedgerEntry[]): PortfolioMetrics['methodSeries'] {
  const map = new Map<string, number>();
  for (const e of entries) {
    const method = e.method;
    map.set(method, (map.get(method) ?? 0) + e.amount);
  }
  const methods = ['MOBILE_MONEY', 'CARD', 'BANK', 'CASH'];
  return methods
    .filter((m) => map.has(m))
    .map((m) => ({
      label: METHOD_LABELS[m] ?? m,
      value: map.get(m) ?? 0,
      color: METHOD_COLORS[m] ?? '#9CA3AF'
    }));
}

function upcomingDue(list: Customer[]): PortfolioMetrics['upcoming'] {
  return [...list]
    .sort((a, b) => a.nextPaymentDueEpochMillis - b.nextPaymentDueEpochMillis)
    .slice(0, 5)
    .map((c) => ({
      id: c.id,
      name: c.customerName,
      phone: c.phoneNumber,
      ms: c.nextPaymentDueEpochMillis - Date.now(),
      amount: c.dailyRate,
      status: c.status
    }));
}

export const portfolioMetrics: Readable<PortfolioMetrics> = derived(
  [customers, payments],
  ([$customers, $payments]) => {
    const list = $customers;
    const totalLoaned = list.reduce((s, c) => s + c.totalLoanAmount, 0);
    const totalPaid = list.reduce((s, c) => s + c.amountPaid, 0);
    const totalOutstanding = list.reduce((s, c) => s + c.remainingBalance, 0);
    const avgDailyRate = list.length ? list.reduce((s, c) => s + c.dailyRate, 0) / list.length : 0;
    const models = deviceDistribution(list).map((d) => ({
      name: d.name,
      count: d.value,
      outstanding: list
        .filter((c) => c.deviceModel === d.name)
        .reduce((s, c) => s + c.remainingBalance, 0)
    }));
    return {
      totalAccounts: list.length,
      statusBreakdown: statusBreakdown(list),
      paidRatio: paidRatio(list),
      totalLoaned,
      totalPaid,
      totalOutstanding,
      avgDailyRate,
      models,
      deviceDistribution: deviceDistribution(list),
      collectionSeries: collectionSeriesFromPayments($payments),
      methodSeries: methodSeriesFromPayments($payments),
      upcoming: upcomingDue(list),
      health: healthScore(list)
    };
  }
);

export const kpis: Readable<KpiSummary> = derived(customers, ($customers) => {
  let activeNodes = 0;
  let lockedCount = 0;
  let warningCount = 0;
  let paidCount = 0;
  let totalOutstanding = 0;
  for (const c of $customers) {
    if (c.status === 'ACTIVE') activeNodes++;
    else if (c.status === 'WARNING') warningCount++;
    else lockedCount++;
    if (c.amountPaid >= c.totalLoanAmount) paidCount++;
    totalOutstanding += c.remainingBalance;
  }
  return {
    activeNodes,
    activeCount: activeNodes,
    lockedCount,
    warningCount,
    paidCount,
    totalOutstanding,
    collectedToday: 0,
    totalAccounts: $customers.length,
    collectionHistory: [],
    outstandingHistory: []
  };
});