/**
 * Shared SecurePay domain model. These fields are field-identical across all
 * three apps in the ecosystem (customer-app, agent-app, dealer-dashboard).
 */

export type Status = 'ACTIVE' | 'WARNING' | 'LOCKED';

export interface Customer {
  id: string;
  customerName: string;
  nationalId: string;
  phoneNumber: string;
  imei: string;
  deviceModel: string;
  planName: string;
  totalLoanAmount: number;
  amountPaid: number;
  remainingBalance: number;
  dailyRate: number;
  nextPaymentDueEpochMillis: number;
  status: Status;
}

/** A single KPI tile displayed on the Overview screen. */
export interface Kpi {
  id: string;
  title: string;
  value: string;
  sublabel: string;
  accent: KpiAccent;
}

export type KpiAccent = 'emerald' | 'crimson' | 'amber' | 'neutral';

/** Aggregated portfolio metrics derived from the customer dataset. */
export interface KpiSummary {
  activeNodes: number;
  lockedCount: number;
  warningCount: number;
  totalOutstanding: number;
  collectedToday: number;
}

/** A derived payment-ledger row (synthesized from customer accounts). */
export interface LedgerEntry {
  id: string;
  customerId: string;
  customerName: string;
  imei: string;
  amount: number;
  dateEpochMillis: number;
  method: PaymentMethod;
  reference: string;
}

export type PaymentMethod = 'M-PESA' | 'CARD' | 'BANK' | 'CASH';
