import type { CustomerNode, KpiSummary, RemoteCommandResult } from '$lib/models/dashboard';

const customers: CustomerNode[] = [
  {
    id: 'CUS-1048',
    customerName: 'Amina Otieno',
    phoneNumber: '+254 711 248 100',
    imei: '353918110248001',
    status: 'ACTIVE',
    balanceCents: 1850000,
    dueAtIso: '2026-06-07T09:00:00.000Z',
    dealerRegion: 'Nairobi East'
  },
  {
    id: 'CUS-1057',
    customerName: 'Daniel Mwangi',
    phoneNumber: '+254 722 104 800',
    imei: '353918110248579',
    status: 'LOCKED',
    balanceCents: 2310000,
    dueAtIso: '2026-06-05T15:30:00.000Z',
    dealerRegion: 'Kiambu'
  },
  {
    id: 'CUS-1082',
    customerName: 'Faith Njeri',
    phoneNumber: '+254 733 810 450',
    imei: '353918110248883',
    status: 'WARNING',
    balanceCents: 940000,
    dueAtIso: '2026-06-06T18:45:00.000Z',
    dealerRegion: 'Machakos'
  },
  {
    id: 'CUS-1095',
    customerName: 'Joseph Okello',
    phoneNumber: '+254 745 900 221',
    imei: '353918110248994',
    status: 'ACTIVE',
    balanceCents: 1285000,
    dueAtIso: '2026-06-09T07:15:00.000Z',
    dealerRegion: 'Kisumu'
  }
];

export function fetchCustomerNodes(): CustomerNode[] {
  return customers.map((customer) => ({ ...customer }));
}

export function fetchKpiSummary(): KpiSummary {
  const summary = customers.reduce(
    (acc, customer) => {
      acc.portfolioBalanceCents += customer.balanceCents;
      if (customer.status === 'ACTIVE') acc.activeNodes += 1;
      if (customer.status === 'LOCKED') acc.lockedNodes += 1;
      if (customer.status === 'WARNING') acc.warningNodes += 1;
      return acc;
    },
    {
      activeNodes: 0,
      lockedNodes: 0,
      warningNodes: 0,
      portfolioBalanceCents: 0,
      collectedTodayCents: 680000
    }
  );

  return summary;
}

export function extendTimer(customerId: string): RemoteCommandResult {
  const customer = customers.find((item) => item.id === customerId);
  if (customer) {
    customer.status = 'ACTIVE';
    customer.dueAtIso = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
  }

  return {
    commandId: crypto.randomUUID(),
    customerId,
    command: 'EXTEND_TIMER',
    message: `Extended payment timer for ${customerId}`,
    issuedAtIso: new Date().toISOString()
  };
}

export function forceRemoteLock(customerId: string): RemoteCommandResult {
  const customer = customers.find((item) => item.id === customerId);
  if (customer) {
    customer.status = 'LOCKED';
    customer.dueAtIso = new Date(Date.now() - 1000).toISOString();
  }

  return {
    commandId: crypto.randomUUID(),
    customerId,
    command: 'FORCE_REMOTE_LOCK',
    message: `Remote lock queued for ${customerId}`,
    issuedAtIso: new Date().toISOString()
  };
}
