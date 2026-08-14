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

/**
 * Normalize any epoch timestamp (seconds or milliseconds, number or string) to milliseconds.
 * Prevents the "20672d ago" bug where unixepoch seconds (~1.78e9) were subtracted from ms (~1.78e12).
 */
export function toEpochMillis(val: unknown): number {
  if (val == null) return 0;
  if (typeof val === 'number') {
    if (!Number.isFinite(val) || val <= 0) return 0;
    // Unix timestamps in seconds (< 1e11) are converted to milliseconds (* 1000)
    return val < 1e11 ? val * 1000 : val;
  }
  if (typeof val === 'string') {
    const num = Number(val);
    if (!isNaN(num) && Number.isFinite(num) && num > 0) {
      return num < 1e11 ? num * 1000 : num;
    }
    const parsed = Date.parse(val);
    return isNaN(parsed) ? 0 : parsed;
  }
  if (val instanceof Date) {
    return val.getTime();
  }
  return 0;
}

/** Format an integer amount stored in pesewas as Ghana cedis. */
export function formatCurrency(amountMinor: number): string {
  return GHS.format(amountMinor / 100);
}

/** Format an epoch timestamp (seconds or millis) as a readable date + time. */
export function formatDateTime(epoch: number | unknown): string {
  const ms = toEpochMillis(epoch);
  if (!ms) return '-';
  return DATE_TIME.format(new Date(ms));
}

/** Format an epoch timestamp (seconds or millis) as a date only. */
export function formatDate(epoch: number | unknown): string {
  const ms = toEpochMillis(epoch);
  if (!ms) return '-';
  return DATE.format(new Date(ms));
}

/**
 * Format a remaining duration (in milliseconds) as `Dd HH:MM:SS`.
 * Returns `OVERDUE` when the duration is zero or negative.
 */
export function formatCountdown(remainingMs: number | unknown): string {
  const ms = typeof remainingMs === 'number' ? remainingMs : Number(remainingMs) || 0;
  if (ms <= 0) {
    return 'OVERDUE';
  }

  const totalSeconds = Math.floor(ms / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  const pad = (n: number): string => n.toString().padStart(2, '0');
  return `${days}d ${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}

/** Format a Ghana phone number in `+233 XX XXX XXXX` style. */
export function formatPhone(phone: string): string {
  const digits = (phone || '').replace(/\D/g, '');
  if (digits.length < 7) return phone || '';
  if (digits.startsWith('233') && digits.length === 12) {
    return `+233 ${digits.slice(3, 5)} ${digits.slice(5, 8)} ${digits.slice(8)}`;
  }
  if (digits.startsWith('0') && digits.length === 10) {
    return `${digits.slice(0, 3)} ${digits.slice(3, 6)} ${digits.slice(6)}`;
  }
  return phone;
}

/**
 * Format a relative time (e.g. "in 3h", "2d ago", "5m ago") for upcoming/past epochs.
 * Handles both seconds and milliseconds seamlessly.
 */
export function formatRelative(epoch: number | unknown, now: number = Date.now()): string {
  const ms = toEpochMillis(epoch);
  if (!ms) return '-';
  const diff = ms - now;
  const past = diff < 0;
  const abs = Math.abs(diff);
  const minutes = Math.floor(abs / 60000);
  const hours = Math.floor(abs / 3600000);
  const days = Math.floor(hours / 24);

  let label: string;
  if (days > 0) label = `${days}d`;
  else if (hours > 0) label = `${hours}h`;
  else if (minutes > 0) label = `${minutes}m`;
  else return 'just now';

  return past ? `${label} ago` : `in ${label}`;
}
