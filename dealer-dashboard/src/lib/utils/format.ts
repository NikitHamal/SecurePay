/** Formatting helpers shared by the dashboard views. */

const GHS = new Intl.NumberFormat('en-GH', {
  style: 'currency',
  currency: 'GHS',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

const DATE_TIME = new Intl.DateTimeFormat('en-GH', {
  dateStyle: 'medium',
  timeStyle: 'short'
});

const DATE = new Intl.DateTimeFormat('en-GH', {
  dateStyle: 'medium'
});

/** Format an integer amount stored in pesewas as Ghana cedis. */
export function formatCurrency(amountMinor: number): string {
  return GHS.format(amountMinor / 100);
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

/** Format a Ghana phone number in `+233 XX XXX XXXX` style. */
export function formatPhone(phone: string): string {
  const digits = phone.replace(/\D/g, '');
  if (digits.length < 7) return phone;
  if (digits.startsWith('233') && digits.length === 12) {
    return `+233 ${digits.slice(3, 5)} ${digits.slice(5, 8)} ${digits.slice(8)}`;
  }
  if (digits.startsWith('0') && digits.length === 10) {
    return `${digits.slice(0, 3)} ${digits.slice(3, 6)} ${digits.slice(6)}`;
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
