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

/** Format a phone number in `+254 7XX XXX XXX` style for readability. */
export function formatPhone(phone: string): string {
  const digits = phone.replace(/\D/g, '');
  if (digits.length < 7) return phone;
  if (digits.startsWith('254') && digits.length === 12) {
    return `+254 ${digits.slice(3, 6)} ${digits.slice(6, 9)} ${digits.slice(9)}`;
  }
  return phone;
}

/**
 * Format a relative time (e.g. "in 3h", "2d ago") for upcoming/past epochs.
 */
export function formatRelative(epochMillis: number, now: number = Date.now()): string {
  const diff = epochMillis - now;
  const past = diff < 0;
  const abs = Math.abs(diff);
  const hours = Math.floor(abs / (60 * 60 * 1000));
  const days = Math.floor(hours / 24);
  let label: string;
  if (days > 0) label = `${days}d`;
  else if (hours > 0) label = `${hours}h`;
  else label = `${Math.max(1, Math.floor(abs / (60 * 1000)))}m`;
  return past ? `${label} ago` : `in ${label}`;
}
