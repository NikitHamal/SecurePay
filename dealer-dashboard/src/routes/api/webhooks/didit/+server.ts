import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, generateToken } from '$lib/api/server';

function normalizeNumbers(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(normalizeNumbers);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>).map(([k, v]) => [k, normalizeNumbers(v)]));
  }
  if (typeof value === 'number' && Number.isFinite(value) && value % 1 === 0) return Math.trunc(value);
  return value;
}

function sortKeys(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sortKeys);
  if (value && typeof value === 'object') {
    return Object.keys(value as object).sort().reduce<Record<string, unknown>>((out, key) => {
      out[key] = sortKeys((value as Record<string, unknown>)[key]);
      return out;
    }, {});
  }
  return value;
}

function hexToBytes(hex: string): Uint8Array | null {
  if (!/^[a-fA-F0-9]{64}$/.test(hex)) return null;
  const bytes = new Uint8Array(32);
  for (let i = 0; i < hex.length; i += 2) bytes[i / 2] = Number.parseInt(hex.slice(i, i + 2), 16);
  return bytes;
}

async function verifySignature(rawBody: string, signature: string, secret: string): Promise<boolean> {
  try {
    const signatureBytes = hexToBytes(signature);
    if (!signatureBytes) return false;
    const canonical = JSON.stringify(sortKeys(normalizeNumbers(JSON.parse(rawBody))));
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey('raw', encoder.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['verify']);
    return crypto.subtle.verify('HMAC', key, signatureBytes as BufferSource, encoder.encode(canonical));
  } catch {
    return false;
  }
}

export const POST: RequestHandler = async ({ request, platform }) => {
  const secret = platform?.env?.DIDIT_WEBHOOK_SECRET;
  const signature = request.headers.get('x-signature-v2') ?? '';
  const timestamp = Number(request.headers.get('x-timestamp'));
  const raw = await request.text();

  if (!secret || !signature) return json({ error: 'Missing webhook signature' }, { status: 401 });
  if (!Number.isFinite(timestamp) || Math.abs(Math.floor(Date.now() / 1000) - timestamp) > 300) {
    return json({ error: 'Stale webhook' }, { status: 401 });
  }
  if (!(await verifySignature(raw, signature, secret))) {
    return json({ error: 'Invalid webhook signature' }, { status: 401 });
  }

  let payload: Record<string, unknown>;
  try {
    payload = JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return json({ error: 'Invalid JSON' }, { status: 400 });
  }

  const sessionId = String(payload.session_id ?? '').trim();
  const accountId = String(payload.vendor_data ?? '').trim();
  const status = String(payload.status ?? '').trim();
  const webhookType = String(payload.webhook_type ?? 'unknown').trim();
  const eventId = String(payload.event_id ?? `${sessionId}:${status}:${timestamp}`).trim();
  if (!sessionId || !accountId) return json({ received: true, ignored: 'missing session or vendor data' });

  const db = getDb({ platform });
  const account = await db.prepare(`
    SELECT id, customer_name, enrolled_by, branch_id, agency_id, didit_session_id
    FROM accounts
    WHERE id = ?
  `).bind(accountId).first<{
    id: string;
    customer_name: string;
    enrolled_by: string | null;
    branch_id: string | null;
    agency_id: string | null;
    didit_session_id: string | null;
  }>();
  if (!account) return json({ received: true, ignored: 'unknown account' });
  if (account.didit_session_id && account.didit_session_id !== sessionId) {
    return json({ received: true, ignored: 'session mismatch' });
  }

  const now = Math.floor(Date.now() / 1000);
  const dedupe = await db.prepare(`
    INSERT OR IGNORE INTO didit_webhook_events
      (event_id, session_id, account_id, webhook_type, status, received_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).bind(eventId, sessionId, accountId, webhookType, status, now).run();
  if (Number(dedupe.meta.changes ?? 0) !== 1) return json({ received: true, duplicate: true });

  const normalizedStatus = status.toLowerCase();
  const approved = normalizedStatus === 'approved';
  const declined = normalizedStatus === 'declined' || normalizedStatus === 'kyc expired';
  const decision = payload.decision ? JSON.stringify(payload.decision) : null;

  await db.prepare(`
    UPDATE accounts
    SET ghana_card_verified = ?,
        ghana_card_status = ?,
        ghana_card_verified_at = CASE WHEN ? = 1 THEN ? ELSE ghana_card_verified_at END,
        didit_session_id = ?,
        didit_decision = COALESCE(?, didit_decision),
        updated_at = ?
    WHERE id = ?
  `).bind(approved ? 1 : (declined ? 0 : Number(false)), status || 'Unknown', approved ? 1 : 0, now, sessionId, decision, now, accountId).run();

  if (approved || declined) {
    const recipients = await db.prepare(`
      SELECT id FROM dealers
      WHERE role = 'SUPER_ADMIN'
         OR id = ?
         OR (role = 'BRANCH_ADMIN' AND branch_id = ?)
         OR (role = 'AGENCY_OWNER' AND agency_id = ?)
    `).bind(account.enrolled_by ?? '', account.branch_id ?? '', account.agency_id ?? '').all();

    for (const recipient of recipients.results) {
      await db.prepare(`
        INSERT INTO notifications
          (id, recipient_id, type, title, message, related_entity_type, related_entity_id, created_at)
        VALUES (?, ?, ?, ?, ?, 'account', ?, ?)
      `).bind(
        `NOTIF-${generateToken(6).toUpperCase()}`,
        String(recipient.id),
        approved ? 'KYC_VERIFIED' : 'KYC_DECLINED',
        approved ? 'Identity verified' : 'Identity verification needs attention',
        `${account.customer_name} verification status: ${status}`,
        accountId,
        now
      ).run();
    }
  }

  return json({ received: true, accountId, status });
};
