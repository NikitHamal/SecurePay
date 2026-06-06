import { derived, writable, type Readable, type Writable } from 'svelte/store';
import type { Customer, KpiSummary } from '$lib/types';
import {
  listCustomers as apiListCustomers,
  extendTimer as apiExtendTimer,
  forceRemoteLock as apiForceRemoteLock
} from '$lib/api/client';

export const customers: Writable<Customer[]> = writable<Customer[]>([]);
export const loading: Writable<boolean> = writable<boolean>(false);
export const error: Writable<string | null> = writable<string | null>(null);
export const pending: Writable<Set<string>> = writable<Set<string>>(new Set());

function setPending(id: string, isPending: boolean): void {
  pending.update((current) => {
    const next = new Set(current);
    if (isPending) {
      next.add(id);
    } else {
      next.delete(id);
    }
    return next;
  });
}

function replaceCustomer(updated: Customer): void {
  customers.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
}

export async function load(): Promise<void> {
  loading.set(true);
  error.set(null);
  try {
    const data = await apiListCustomers();
    customers.set(data);
  } catch (err) {
    error.set(err instanceof Error ? err.message : 'Failed to load customers');
  } finally {
    loading.set(false);
  }
}

export async function extendTimer(id: string, hours: number): Promise<void> {
  setPending(id, true);
  error.set(null);
  try {
    const updated = await apiExtendTimer(id, hours);
    replaceCustomer(updated);
  } catch (err) {
    error.set(err instanceof Error ? err.message : 'Failed to extend timer');
  } finally {
    setPending(id, false);
  }
}

export async function forceRemoteLock(id: string): Promise<void> {
  setPending(id, true);
  error.set(null);
  try {
    const updated = await apiForceRemoteLock(id);
    replaceCustomer(updated);
  } catch (err) {
    error.set(err instanceof Error ? err.message : 'Failed to lock device');
  } finally {
    setPending(id, false);
  }
}

export const kpiSummary: Readable<KpiSummary> = derived(customers, ($customers) => {
  const summary: KpiSummary = {
    activeNodes: 0,
    lockedCount: 0,
    warningCount: 0,
    totalOutstanding: 0,
    collectedToday: 0
  };

  for (const customer of $customers) {
    if (customer.status === 'ACTIVE') {
      summary.activeNodes += 1;
    } else if (customer.status === 'WARNING') {
      summary.warningCount += 1;
    } else {
      summary.lockedCount += 1;
    }
    summary.totalOutstanding += customer.remainingBalance;
    if (customer.status !== 'LOCKED') {
      summary.collectedToday += customer.dailyRate;
    }
  }

  return summary;
});