export type DeviceStatus = 'ACTIVE' | 'WARNING' | 'LOCKED';

export type CustomerNode = {
  id: string;
  customerName: string;
  phoneNumber: string;
  imei: string;
  status: DeviceStatus;
  balanceCents: number;
  dueAtIso: string;
  dealerRegion: string;
};

export type KpiSummary = {
  activeNodes: number;
  lockedNodes: number;
  warningNodes: number;
  portfolioBalanceCents: number;
  collectedTodayCents: number;
};

export type RemoteCommandResult = {
  commandId: string;
  customerId: string;
  command: 'EXTEND_TIMER' | 'FORCE_REMOTE_LOCK';
  message: string;
  issuedAtIso: string;
};
