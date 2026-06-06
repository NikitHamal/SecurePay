import { derived, type Readable } from 'svelte/store';
import type { Customer, KpiSummary, Status } from '$lib/types';
import { customers } from './customers';

/** Portfolio health score: weighted by paid ratio and status. 0-100. */
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

const PALETTE: Record<string, string> = {
  'Samsung Galaxy A15': '#10B981',
  'Xiaomi Redmi 13C': '#38BDF8',
  'Tecno Spark 20': '#A78BFA',
  'Samsung Galaxy A25': '#F59E0B',
  'Infinix Hot 40': '#F472B6',
  'Oppo A18': '#34D399',
  'Tecno Camon 30': '#FBBF24',
  'Samsung Galaxy A05s': '#60A5FA'
};

const FALLBACK_PALETTE = ['#10B981', '#38BDF8', '#A78BFA', '#F59E0B', '#F472B6', '#34D399', '#FBBF24', '#60A5FA'];

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
    color: PALETTE[name] ?? FALLBACK_PALETTE[i % FALLBACK_PALETTE.length]
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

/** Mock 14-day collection series derived deterministically from dataset. */
function collectionSeries(list: Customer[]): PortfolioMetrics['collectionSeries'] {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const days: { label: string; value: number }[] = [];
  for (let i = 13; i >= 0; i -= 1) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    // Deterministic pseudo-random based on day index + dataset
    const seed = (i * 37 + list.length * 13) % 11;
    const baseline = list.reduce((s, c) => s + c.dailyRate, 0);
    const noise = (seed - 5) * (baseline * 0.04);
    const value = Math.max(0, Math.round((baseline * 0.7) + baseline + noise));
    days.push({
      label: d.toLocaleDateString('en-KE', { day: '2-digit', month: 'short' }),
      value
    });
  }
  return days;
}

/** Mock payment-method series (synthesized from list). */
function methodSeries(list: Customer[]): PortfolioMetrics['methodSeries'] {
  const total = list.reduce((s, c) => s + c.amountPaid, 0);
  // Pretend M-PESA dominates, then CARD, BANK, CASH
  return [
    { label: 'M-PESA', value: Math.round(total * 0.62), color: '#10B981' },
    { label: 'CARD', value: Math.round(total * 0.18), color: '#F59E0B' },
    { label: 'BANK', value: Math.round(total * 0.12), color: '#38BDF8' },
    { label: 'CASH', value: Math.round(total * 0.08), color: '#A78BFA' }
  ];
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

export const portfolioMetrics: Readable<PortfolioMetrics> = derived(customers, ($customers) => {
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
    collectionSeries: collectionSeries(list),
    methodSeries: methodSeries(list),
    upcoming: upcomingDue(list),
    health: healthScore(list)
  };
});

/** Re-export the KPI summary derived store under a stable alias. */
export const kpis: Readable<KpiSummary> = derived(customers, ($customers) => {
  const counts = { activeNodes: 0, lockedCount: 0, warningCount: 0 };
  let totalOutstanding = 0;
  let collectedToday = 0;
  for (const c of $customers) {
    if (c.status === 'ACTIVE') counts.activeNodes += 1;
    else if (c.status === 'WARNING') counts.warningCount += 1;
    else counts.lockedCount += 1;
    totalOutstanding += c.remainingBalance;
    if (c.status !== 'LOCKED') collectedToday += c.dailyRate;
  }
  return { ...counts, totalOutstanding, collectedToday };
});
