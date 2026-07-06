import type { Customer, KpiSummary, LedgerEntry, PaymentMethod } from '$lib/types';

const API_BASE = '/api';

let authToken: string | null = typeof window !== 'undefined' ? localStorage.getItem('securepay_token') : null;


export function setToken(token: string): void {
  authToken = token;
  if (typeof window !== 'undefined') {
    localStorage.setItem('securepay_token', token);
  }
}

export function getToken(): string | null {
  if (authToken) return authToken;
  if (typeof window !== 'undefined') {
    authToken = localStorage.getItem('securepay_token');
  }
  return authToken;
}

export function clearToken(): void {
  authToken = null;
  if (typeof window !== 'undefined') {
    localStorage.removeItem('securepay_token');
  }
}

function headers(): Record<string, string> {
  const h: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) h['Authorization'] = `Bearer ${token}`;
  return h;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { ...headers(), ...(options.headers as Record<string, string> || {}) }
  });

  if (!res.ok) {
    if (res.status === 401) {
      clearToken();
    }
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `Request failed: ${res.status}`);
  }

  return res.json();
}

export function computeStatus(nextPaymentDueEpochMillis: number, now = Date.now()): 'ACTIVE' | 'WARNING' | 'LOCKED' {
  const DAY_MS = 24 * 60 * 60 * 1000;
  if (now >= nextPaymentDueEpochMillis) return 'LOCKED';
  if (nextPaymentDueEpochMillis - now <= DAY_MS) return 'WARNING';
  return 'ACTIVE';
}

export async function login(email: string, password: string): Promise<{ token: string; dealer: { id: string; name: string; email: string } }> {
  const result = await request<{ token: string; dealer: { id: string; name: string; email: string } }>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password })
  });
  setToken(result.token);
  return result;
}

export async function logout(): Promise<void> {
  try {
    await request('/auth/logout', { method: 'POST' });
  } finally {
    clearToken();
  }
}

export async function listCustomers(status?: string): Promise<Customer[]> {
  const params = status ? `?status=${status}` : '';
  return request<Customer[]>(`/accounts${params}`);
}

export async function getAccount(id: string): Promise<Customer> {
  return request<Customer>(`/accounts/${id}`);
}

export async function getAccountLocations(id: string): Promise<{
  latitude: number;
  longitude: number;
  accuracy: number | null;
  batteryLevel: number | null;
  timestamp: number;
}[]> {
  try {
    const data = await request<any>(`/accounts/${id}/location`);
    if (data && typeof data === 'object' && !Array.isArray(data)) {
      return [{
        latitude: Number(data.latitude ?? data.lat),
        longitude: Number(data.longitude ?? data.lng),
        accuracy: data.accuracy != null ? Number(data.accuracy) : null,
        batteryLevel: data.batteryLevel != null ? Number(data.batteryLevel) : (data.battery != null ? Number(data.battery) : null),
        timestamp: Number(data.timestamp)
      }];
    }
    return Array.isArray(data) ? data : [];
  } catch (e) {
    return [];
  }
}


export async function createAccount(data: {
  customerName: string;
  nationalId: string;
  phoneNumber: string;
  imei: string;
  planId?: string;
  dailyRate?: number;
  totalAmount?: number;
  termDays?: number;
  downPayment?: number;
}): Promise<Customer> {
  return request<Customer>('/accounts', {
    method: 'POST',
    body: JSON.stringify(data)
  });
}

export async function extendTimer(id: string, hours: number): Promise<Customer> {
  return request<Customer>(`/accounts/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ nextPaymentDue: Date.now() + hours * 24 * 60 * 60 * 1000 })
  });
}

export async function updateAccount(id: string, data: {
  customerName?: string;
  nationalId?: string;
  phoneNumber?: string;
  dailyRate?: number;
  totalLoanAmount?: number;
  termDays?: number;
  isStolen?: boolean;
}): Promise<Customer> {
  return request<Customer>(`/accounts/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(data)
  });
}

export async function forceRemoteLock(id: string): Promise<Customer> {
  return request<Customer>(`/accounts/${id}/force-lock`, { method: 'POST' });
}

export async function deleteAccount(id: string): Promise<{ success: boolean; id: string; deviceId?: string }> {
  return request<{ success: boolean; id: string; deviceId?: string }>(`/accounts/${id}`, { method: 'DELETE' });
}

export async function forceRemoteUnlock(id: string): Promise<Customer> {
  return request<Customer>(`/accounts/${id}/force-unlock`, { method: 'POST' });
}

export async function approveRelease(id: string, allowEarlyRelease = false, note?: string): Promise<Customer> {
  return request<Customer>(`/accounts/${id}/release`, {
    method: 'POST',
    body: JSON.stringify({ allowEarlyRelease, note })
  });
}

export async function recordPayment(data: {
  accountId: string;
  amount: number;
  method: string;
  reference?: string;
}): Promise<{ payment: { id: string }; account: Customer }> {
  return request('/payments', {
    method: 'POST',
    body: JSON.stringify(data)
  });
}

export async function listLedger(method?: string, accountId?: string): Promise<LedgerEntry[]> {
  const params = new URLSearchParams();
  if (method) params.set('method', method);
  if (accountId) params.set('accountId', accountId);
  const qs = params.toString();
  return request<LedgerEntry[]>(`/ledger${qs ? `?${qs}` : ''}`);
}

export async function getKpis(): Promise<KpiSummary> {
  return request<KpiSummary>('/kpis');
}

export async function listDevices(): Promise<{ id: string; imei: string; model: string; status: string; createdAt: number }[]> {
  return request('/devices');
}

export async function addDevice(imei: string, model: string): Promise<{ id: string; imei: string; model: string; status: string }> {
  return request('/devices', {
    method: 'POST',
    body: JSON.stringify({ imei, model })
  });
}

export async function deleteDevice(id: string, force = false): Promise<{ success: boolean; id: string }> {
  const qs = force ? '?force=true' : '';
  return request<{ success: boolean; id: string }>(`/devices/${id}${qs}`, { method: 'DELETE' });
}

export async function listPlans(): Promise<{ id: string; name: string; termDays: number; totalAmount: number; dailyRate: number; minDownPayment: number }[]> {
  return request('/plans');
}

export async function deviceCheck(imei: string): Promise<{
  enrolled: boolean;
  imei: string;
  deviceModel?: string;
  status?: string;
  accountId?: string;
  customerName?: string;
  nextPaymentDue?: number;
  dailyRate?: number;
}> {
  return request(`/device/check?imei=${encodeURIComponent(imei)}`);
}

export async function deviceHeartbeat(imei: string): Promise<{
  enrolled: boolean;
  status: string;
  nextPaymentDue: number;
  dailyRate: number;
  amountPaid: number;
  totalLoanAmount: number;
  remainingBalance: number;
  serverTime: number;
}> {
  return request('/device/heartbeat', {
    method: 'POST',
    body: JSON.stringify({ imei })
  });
}
export interface SecurityPolicy {
  version: number;
  frpEnabled: boolean;
  frpAccountIds: string[];
  blockFactoryReset: boolean;
  blockSafeBoot: boolean;
  blockDeveloperOptions: boolean;
  blockUnknownSources: boolean;
  blockAccountModification: boolean;
}

export async function getSecurityPolicy(): Promise<SecurityPolicy> {
  return request<SecurityPolicy>('/security-policy');
}

export async function updateSecurityPolicy(frpAccountIds: string[]): Promise<SecurityPolicy> {
  return request<SecurityPolicy>('/security-policy', {
    method: 'PUT',
    body: JSON.stringify({ frpAccountIds })
  });
}

export interface DeviceLog {
  id: number;
  tag: string;
  message: string;
  level: string;
  time: string;
}

export async function listDeviceLogs(): Promise<DeviceLog[]> {
  return request<DeviceLog[]>('/device/logs');
}

export async function clearDeviceLogs(): Promise<{ success: boolean }> {
  return request<{ success: boolean }>('/device/logs', { method: 'DELETE' });
}

