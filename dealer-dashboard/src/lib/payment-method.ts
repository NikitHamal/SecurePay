import type { PaymentMethod } from '$lib/types';

const ALIASES: Record<string, PaymentMethod> = {
  MOBILE_MONEY: 'MOBILE_MONEY',
  MOBILEMONEY: 'MOBILE_MONEY',
  MOMO: 'MOBILE_MONEY',
  MPESA: 'MOBILE_MONEY',
  M_PESA: 'MOBILE_MONEY',
  CARD: 'CARD',
  BANK: 'BANK',
  BANK_TRANSFER: 'BANK',
  CASH: 'CASH'
};

export function parsePaymentMethod(value: unknown): PaymentMethod | null {
  if (typeof value !== 'string') return null;
  const normalized = value.trim().toUpperCase().replace(/[\s-]+/g, '_');
  return ALIASES[normalized] ?? null;
}

export function paymentMethodStorageValue(method: PaymentMethod): string {
  return method.toLowerCase();
}
