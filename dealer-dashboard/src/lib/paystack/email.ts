/**
 * Derive an email address for the customer. Paystack requires an email, but in
 * Ghana mobile-money flows many customers do not have an email on file. In that
 * case we synthesize a stable placeholder from the phone number. Dealers can
 * update it.
 */
export function getCustomerEmail(account: Record<string, any>, normalizedPhone: string): string {
  const direct = typeof account?.email === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(account.email.trim())
    ? account.email.trim()
    : '';
  if (direct) return direct;
  const digits = (normalizedPhone || '').replace(/\D/g, '');
  return `${digits || '0000000000'}@ussd.touchbase.com`;
}
