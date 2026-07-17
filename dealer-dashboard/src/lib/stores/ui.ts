import { writable } from 'svelte/store';

export const sidebarOpen = writable(false);
export const newLoanOpen = writable(false);
export const addDeviceOpen = writable(false);
export const provisionOpen = writable(false);
export const provisionInitialImei = writable('');

/**
 * Pre-filled values for the New Loan modal (used when launching from an
 * inventory row, provisioning flow, etc.).
 */
export const newLoanPrefill = writable<{ imei?: string; deviceModel?: string; customerName?: string; phone?: string }>({});

export function openNewLoan(prefill: { imei?: string; deviceModel?: string; customerName?: string; phone?: string } = {}) {
  newLoanPrefill.set(prefill);
  newLoanOpen.set(true);
}
export function openAddDevice() { addDeviceOpen.set(true); }
export function openProvision(imei = '') {
  provisionInitialImei.set(imei);
  provisionOpen.set(true);
}
