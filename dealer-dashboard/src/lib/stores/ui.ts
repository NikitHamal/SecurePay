import { writable } from 'svelte/store';

export const sidebarOpen = writable(false);
export const newLoanOpen = writable(false);
export const addDeviceOpen = writable(false);
export const provisionOpen = writable(false);
export const provisionInitialImei = writable('');

export function openNewLoan() { newLoanOpen.set(true); }
export function openAddDevice() { addDeviceOpen.set(true); }
export function openProvision(imei = '') {
  provisionInitialImei.set(imei);
  provisionOpen.set(true);
}
