/** Formatting helpers shared by the dashboard views. */

const KES = new Intl.NumberFormat('en-KE', {
  style: 'currency',
  currency: 'KES',
  maximumFractionDigits: 0
});

const DATE_TIME = new Intl.DateTimeFormat('en-KE', {
  dateStyle: 'medium',
  timeStyle: 'short'
});

const DATE = new Intl.DateTimeFormat('en-KE', {
  dateStyle: 'medium'
});

/** Format a numeric amount as Kenyan shillings. */
export function formatCurrency(amount: number): string {
  return KES.format(amount);
}

/** Format an epoch-millis timestamp as a readable date + time. */
export function formatDateTime(epochMillis: number): string {
  return DATE_TIME.format(new Date(epochMillis));
}

/** Format an epoch-millis timestamp as a date only. */
export function formatDate(epochMillis: number): string {
  return DATE.format(new Date(epochMillis));
}

/**
 * Format a remaining duration (in milliseconds) as `Dd HH:MM:SS`.
 * Returns `OVERDUE` when the duration is zero or negative.
 */
export function formatCountdown(remainingMs: number): string {
  if (remainingMs <= 0) {
    return 'OVERDUE';
  }

  const totalSeconds = Math.floor(remainingMs / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  const pad = (n: number): string => n.toString().padStart(2, '0');
  return `${days}d ${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}
