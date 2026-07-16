import { writable, type Writable } from 'svelte/store';
import type { LedgerEntry } from '$lib/types';
import { listLedger as apiListLedger } from '$lib/api/client';

export const payments: Writable<LedgerEntry[]> = writable<LedgerEntry[]>([]);
export const paymentsLoading: Writable<boolean> = writable<boolean>(false);
export const paymentsError: Writable<string | null> = writable<string | null>(null);

export async function loadPayments(): Promise<void> {
  paymentsLoading.set(true);
  paymentsError.set(null);
  try {
    const data = await apiListLedger();
    payments.set(data);
  } catch (err) {
    paymentsError.set(err instanceof Error ? err.message : 'Failed to load payments');
  } finally {
    paymentsLoading.set(false);
  }
}