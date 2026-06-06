export type DeviceStatus = 'active' | 'warning' | 'locked';

export type PaymentStatus = 'current' | 'due_soon' | 'overdue';

export type SectionKey = 'overview' | 'inventory' | 'customers' | 'ledger';

export type CustomerNode = {
  id: string;
  customerName: string;
  phoneNumber: string;
  imei: string;
  region: string;
  status: DeviceStatus;
  paymentStatus: PaymentStatus;
  outstandingBalanceCents: number;
  nextDueEpochMs: number;
  lastSeenMinutesAgo: number;
};

export type InventoryDevice = {
  imei: string;
  model: string;
  batchId: string;
  status: 'available' | 'assigned' | 'locked';
  dealerLocation: string;
};

export type LedgerEntry = {
  id: string;
  customerName: string;
  amountCents: number;
  channel: 'cash' | 'mobile_money' | 'card';
  postedAt: string;
  status: 'settled' | 'pending';
};
