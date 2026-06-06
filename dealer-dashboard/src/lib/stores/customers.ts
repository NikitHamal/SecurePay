import { derived, writable, type Readable, type Writable } from 'svelte/store';
import type { Customer, KpiSummary } from '$lib/types';
import {
  extendTimer as apiExtendTimer,
  forceRemoteLock as apiForceRemoteLock,
  listCustomers
} from '$lib/api/mockApi';

/** Master list of customer accounts. */
export const customers: Writable<Customer[]> = writable<Customer[]>([]);

/** True while a network-style operation is in flight. */
export const loading: Writable<boolean> = writable<boolean>(false);

/** Holds the last error message, or null. */
export const error: Writable<string | null> = writable<string | null>(null);

/** IDs of accounts with an in-flight action (for per-row button state). */
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

/** Load (or reload) the full customer dataset. */
export async function load(): Promise<void> {
  loading.set(true);
  error.set(null);
  try {
    const data = await listCustomers();
    customers.set(data);
  } catch (err) {
    error.set(err instanceof Error ? err.message : 'Failed to load customers');
  } finally {
    loading.set(false);
  }
}

/** Extend a customer's payment timer by `hours` and update the store. */
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

/** Force a remote lock on a customer device and update the store. */
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

/** Derived KPI summary that recomputes whenever the customer list changes. */
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
