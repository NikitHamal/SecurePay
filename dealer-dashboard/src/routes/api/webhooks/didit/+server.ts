import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { v4 as uuidv4 } from 'uuid';

export const POST: RequestHandler = async ({ request, platform }) => {
  const db = getDb({ platform });
  const signature = request.headers.get('x-signature-v2');
  const body = await request.text();

  const webhookSecret = platform?.env?.DIDIT_WEBHOOK_SECRET;
  if (!webhookSecret || !signature) {
    return new Response(JSON.stringify({ error: 'Missing signature or secret' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  const isValid = await verifyDiditSignature(body, signature, webhookSecret);
  if (!isValid) {
    return new Response(JSON.stringify({ error: 'Invalid signature' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  let payload: any;
  try {
    payload = JSON.parse(body);
  } catch {
    return new Response(JSON.stringify({ error: 'Invalid JSON' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  const sessionId = payload.data?.session_id || payload.session_id;
  const status = payload.data?.status || payload.status;
  const vendorData = payload.data?.vendor_data || payload.vendor_data;
  const event = payload.event || payload.data?.event;

  if (!sessionId || !vendorData) {
    return json({ received: true, note: 'Missing session_id or vendor_data' });
  }

  const accountId = vendorData;
  const now = Math.floor(Date.now() / 1000);
  const isVerified = status === 'Approved' ? 1 : 0;

  await db.prepare(`
    UPDATE accounts
    SET ghana_card_verified = ?,
        ghana_card_status = ?,
        ghana_card_verified_at = CASE WHEN ? = 1 THEN ? ELSE ghana_card_verified_at END,
        didit_session_id = ?
    WHERE id = ?
  `).bind(isVerified, status, isVerified, now, sessionId, accountId).run();

  if (status === 'Approved') {
    const account = await db.prepare('SELECT dealer_id, customer_name, enrolled_by FROM accounts WHERE id = ?')
      .bind(accountId).first();
    if (account) {
      const recipients = await db.prepare(`
        SELECT id FROM dealers
        WHERE id = ? OR role = 'SUPER_ADMIN'
      `).bind(account.enrolled_by as string).all();

      for (const r of recipients.results) {
        await db.prepare(`
          INSERT INTO notifications (id, recipient_id, type, title, message, related_entity_type, related_entity_id, created_at)
          VALUES (?, ?, 'KYC_VERIFIED', 'Ghana Card Verified', ?, 'account', ?, ?)
        `).bind(uuidv4(), r.id as string,
          `Customer ${(account.customer_name as string)} Ghana Card verification approved`, accountId, now).run();
      }
    }
  }

  return json({ received: true, accountId, status });
};

async function verifyDiditSignature(body: string, signature: string, secret: string): Promise<boolean> {
  try {
    const encoder = new TextEncoder();
    const parsed = JSON.parse(body);
    const sorted = sortKeysRecursive(parsed);
    const canonical = JSON.stringify(sorted);

    const key = await crypto.subtle.importKey(
      'raw',
      encoder.encode(secret),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['verify']
    );

    const sigBytes = hexToUint8Array(signature);
    const dataBytes = encoder.encode(canonical);

    return await crypto.subtle.verify('HMAC', key, sigBytes, dataBytes);
  } catch {
    return false;
  }
}

function sortKeysRecursive(obj: any): any {
  if (obj === null || typeof obj !== 'object') return obj;
  if (Array.isArray(obj)) return obj.map(sortKeysRecursive);
  return Object.keys(obj).sort().reduce((acc: any, key: string) => {
    acc[key] = sortKeysRecursive(obj[key]);
    return acc;
  }, {});
}

function hexToUint8Array(hex: string): Uint8Array {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = parseInt(hex.substr(i, 2), 16);
  }
  return bytes;
}
