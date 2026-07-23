/**
 * Shared SecurePay domain model. These fields are field-identical across all
 * three apps in the ecosystem (customer-app, agent-app, dealer-dashboard).
 */

export type Status = 'ACTIVE' | 'WARNING' | 'LOCKED' | 'STOLEN';

export type DealerRole = 'SUPER_ADMIN' | 'AGENCY_OWNER' | 'BRANCH_ADMIN' | 'AGENT';

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
  lockedByDealer?: number;
  isStolen?: boolean;
  releaseApproved?: boolean;
  releaseApprovedAt?: number | null;
  releasedAt?: number | null;
  customerPhotoPath?: string | null;
  nationalIdFrontPath?: string | null;
  nationalIdBackPath?: string | null;
  termDays: number;
  downPayment: number;
  enrolledBy?: string | null;
  ghanaCardVerified?: boolean;
  ghanaCardStatus?: string | null;
  initialCredentials?: { accountNumber: string; temporaryPin: string };
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
  activeCount: number;
  lockedCount: number;
  warningCount: number;
  paidCount: number;
  totalOutstanding: number;
  collectedToday: number;
  totalAccounts: number;
  collectionHistory: number[];
  outstandingHistory: number[];
}

/** A payment-ledger row from the API. */
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

export type PaymentMethod = 'MOBILE_MONEY' | 'CARD' | 'BANK' | 'CASH';

export interface Agency {
  id: string;
  name: string;
  ownerId: string;
  phone?: string | null;
  region?: string | null;
  isActive: boolean;
  createdAt: number;
}

export interface Branch {
  id: string;
  name: string;
  agencyId: string;
  adminId?: string | null;
  address?: string | null;
  phone?: string | null;
  isActive: boolean;
  createdAt: number;
}

export interface AgentRequest {
  id: string;
  fullName: string;
  email: string;
  phone: string;
  requestedBranchId?: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  createdAt: number;
}

export interface Notification {
  id: string;
  recipientId: string;
  type: string;
  title: string;
  message: string;
  isRead: boolean;
  relatedEntityType?: string | null;
  relatedEntityId?: string | null;
  createdAt: number;
}

export interface Ad {
  id: string;
  title: string;
  description: string;
  imageUrl: string | null;
  linkUrl: string | null;
  isActive: boolean;
  order: number;
  createdAt: string | null;
  updatedAt: string | null;
}
