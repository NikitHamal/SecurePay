import { writable } from 'svelte/store';
import { initialCustomers } from '$lib/data/customers';
import type { CustomerNode } from '$lib/types';

const day = 86_400_000;

export const customers = writable<CustomerNode[]>(initialCustomers);

export async function extendTimer(customerId: string): Promise<void> {
  await simulateNetworkLatency();
  customers.update((rows) =>
    rows.map((row) =>
      row.id === customerId
        ? {
            ...row,
            status: 'active',
            paymentStatus: 'current',
            nextDueEpochMs: Math.max(row.nextDueEpochMs, Date.now()) + day * 7
          }
        : row
    )
  );
}

export async function forceRemoteLock(customerId: string): Promise<void> {
  await simulateNetworkLatency();
  customers.update((rows) =>
    rows.map((row) =>
      row.id === customerId
        ? {
            ...row,
            status: 'locked',
            paymentStatus: 'overdue',
            nextDueEpochMs: Date.now() - 60_000
          }
        : row
    )
  );
}

function simulateNetworkLatency(): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, 450);
  });
}

