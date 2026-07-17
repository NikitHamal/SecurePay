import { derived, writable, type Readable, type Writable } from 'svelte/store';
import type { Customer, KpiSummary } from '$lib/types';
import {
  listCustomers as apiListCustomers,
  extendTimer as apiExtendTimer,
  forceRemoteLock as apiForceRemoteLock,
  approveRelease as apiApproveRelease,
  deleteAccount as apiDeleteAccount,
  updateAccount as apiUpdateAccount
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

export async function approveRelease(id: string, allowEarlyRelease = false): Promise<void> {
  setPending(id, true);
  error.set(null);
  try {
    const updated = await apiApproveRelease(id, allowEarlyRelease, allowEarlyRelease ? 'Manual test/settlement release approved from dashboard' : undefined);
    replaceCustomer(updated);
  } catch (err) {
    error.set(err instanceof Error ? err.message : 'Failed to approve release');
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

export async function updateCustomer(id: string, data: {
  customerName?: string;
  nationalId?: string;
  phoneNumber?: string;
  dailyRate?: number;
  totalLoanAmount?: number;
  termDays?: number;
  isStolen?: boolean;
  customerPhoto?: string | null;
  nationalIdFront?: string | null;
  nationalIdBack?: string | null;
}): Promise<void> {
  setPending(id, true);
  error.set(null);
  try {
    const updated = await apiUpdateAccount(id, data);
    replaceCustomer(updated);
  } catch (err) {
    error.set(err instanceof Error ? err.message : 'Failed to update customer');
  } finally {
    setPending(id, false);
  }
}

export async function deleteCustomer(id: string): Promise<void> {
  setPending(id, true);
  error.set(null);
  try {
    await apiDeleteAccount(id);
    customers.update((list) => list.filter((customer) => customer.id !== id));
  } catch (err) {
    error.set(err instanceof Error ? err.message : 'Failed to delete customer');
  } finally {
    setPending(id, false);
  }
}

export const kpiSummary: Readable<KpiSummary> = derived(customers, ($customers) => {
  let activeNodes = 0;
  let lockedCount = 0;
  let warningCount = 0;
  let paidCount = 0;
  let totalOutstanding = 0;
  let totalAccounts = $customers.length;

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
    totalAccounts,
    collectionHistory: [],
    outstandingHistory: []
  };
});