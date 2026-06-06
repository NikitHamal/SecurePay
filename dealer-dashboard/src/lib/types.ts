// Shared domain model for the SecurePay ecosystem.
// This MUST mirror the customer-app and agent-app definitions so all three
// clients agree on what a financed device node looks like and how its status
// is derived.

export type DeviceStatus = 'ACTIVE' | 'WARNING' | 'LOCKED';

export interface CustomerNode {
  customerId: string;
  customerName: string;
  deviceModel: string;
  imei: string;
  totalLoanAmount: number;
  amountPaid: number;
  remainingBalance: number;
  dailyInstallment: number;
  serverEpochMillis: number;
  nextDueEpochMillis: number;
  status: DeviceStatus;
}

/**
 * Default warning window: a device is flagged WARNING when it is within one day
 * (86_400_000 ms) of its next due time.
 */
export const DEFAULT_WARNING_THRESHOLD_MS = 86_400_000;

/**
 * Evaluate the device status from the current time and the next-due time.
 *
 * - LOCKED  when now is at/after the next due time (payment overdue).
 * - WARNING when now is within `warningThresholdMs` of the next due time.
 * - ACTIVE  otherwise.
 */
export function evaluateStatus(
  now: number,
  nextDue: number,
  warningThresholdMs: number = DEFAULT_WARNING_THRESHOLD_MS
): DeviceStatus {
  if (now >= nextDue) {
    return 'LOCKED';
  }
  if (nextDue - now <= warningThresholdMs) {
    return 'WARNING';
  }
  return 'ACTIVE';
}

/**
 * Format a number as Kenyan Shillings, e.g. 12500 -> "KES 12,500".
 */
export function formatKES(n: number): string {
  const rounded = Math.round(n);
  const formatted = new Intl.NumberFormat('en-KE', {
    maximumFractionDigits: 0
  }).format(rounded);
  return `KES ${formatted}`;
}

/**
 * Format a remaining-time duration (in ms) as HH:MM:SS.
 * Returns "Overdue" when the duration is zero or negative.
 */
export function formatCountdown(ms: number): string {
  if (ms <= 0) {
    return 'Overdue';
  }
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const pad = (v: number): string => v.toString().padStart(2, '0');
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}
