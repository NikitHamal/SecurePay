import type { CustomerNode, InventoryDevice, LedgerEntry } from '$lib/types';

const now = Date.UTC(2026, 5, 6, 12, 0, 0);
const day = 86_400_000;

export const initialCustomers: CustomerNode[] = [
  {
    id: 'SP-CUS-4182',
    customerName: 'Amina Okafor',
    phoneNumber: '+234 801 555 4182',
    imei: '357249105864219',
    region: 'Lagos Central',
    status: 'active',
    paymentStatus: 'current',
    outstandingBalanceCents: 184_500,
    nextDueEpochMs: now + day * 3,
    lastSeenMinutesAgo: 6
  },
  {
    id: 'SP-CUS-3110',
    customerName: 'Daniel Mensah',
    phoneNumber: '+233 24 555 3110',
    imei: '869472064221903',
    region: 'Accra East',
    status: 'warning',
    paymentStatus: 'due_soon',
    outstandingBalanceCents: 72_900,
    nextDueEpochMs: now + 9 * 3_600_000,
    lastSeenMinutesAgo: 18
  },
  {
    id: 'SP-CUS-8874',
    customerName: 'Fatima Bello',
    phoneNumber: '+234 803 555 8874',
    imei: '351756118903772',
    region: 'Kano North',
    status: 'locked',
    paymentStatus: 'overdue',
    outstandingBalanceCents: 118_000,
    nextDueEpochMs: now - day,
    lastSeenMinutesAgo: 3
  },
  {
    id: 'SP-CUS-9028',
    customerName: 'Grace Njeri',
    phoneNumber: '+254 711 555 028',
    imei: '359884104518026',
    region: 'Nairobi West',
    status: 'active',
    paymentStatus: 'current',
    outstandingBalanceCents: 96_200,
    nextDueEpochMs: now + day * 11,
    lastSeenMinutesAgo: 42
  }
];

export const inventoryDevices: InventoryDevice[] = [
  {
    imei: '357249105864219',
    model: 'SecurePay SP-5A',
    batchId: 'BATCH-LAG-05',
    status: 'assigned',
    dealerLocation: 'Lagos Central'
  },
  {
    imei: '869472064221903',
    model: 'SecurePay SP-5A',
    batchId: 'BATCH-ACC-02',
    status: 'assigned',
    dealerLocation: 'Accra East'
  },
  {
    imei: '351756118903772',
    model: 'SecurePay SP-4X',
    batchId: 'BATCH-KAN-01',
    status: 'locked',
    dealerLocation: 'Kano North'
  },
  {
    imei: '356782117420661',
    model: 'SecurePay SP-5A',
    batchId: 'BATCH-NBO-03',
    status: 'available',
    dealerLocation: 'Nairobi West'
  }
];

export const ledgerEntries: LedgerEntry[] = [
  {
    id: 'LED-10284',
    customerName: 'Amina Okafor',
    amountCents: 12_500,
    channel: 'mobile_money',
    postedAt: '2026-06-05 10:28',
    status: 'settled'
  },
  {
    id: 'LED-10285',
    customerName: 'Grace Njeri',
    amountCents: 10_900,
    channel: 'card',
    postedAt: '2026-06-05 14:11',
    status: 'settled'
  },
  {
    id: 'LED-10286',
    customerName: 'Daniel Mensah',
    amountCents: 6_000,
    channel: 'cash',
    postedAt: '2026-06-06 08:42',
    status: 'pending'
  }
];
