import type { D1Database } from '@cloudflare/workers-types';
import type { Status } from '$lib/types';

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

export function computeStatus(nextPaymentDueEpochMillis: number, now = Date.now()): Status {
  if (now >= nextPaymentDueEpochMillis) return 'LOCKED';
  if (nextPaymentDueEpochMillis - now <= DAY_MS) return 'WARNING';
  return 'ACTIVE';
}

export function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}

export function errorResponse(message: string, status = 400): Response {
  return jsonResponse({ error: message }, status);
}

export function parseBody<T>(request: Request): Promise<T> {
  return request.json() as Promise<T>;
}

export function getDb(event: { platform?: App.Platform | null }): D1Database {
  if (!event.platform?.env?.DB) {
    throw new Error('D1 database not available');
  }
  return event.platform.env.DB;
}

export function getJwtSecret(event: { platform?: App.Platform | null }): string {
  if (!event.platform?.env?.JWT_SECRET) {
    throw new Error('JWT_SECRET not configured');
  }
  return event.platform.env.JWT_SECRET;
}