import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, generateToken } from '$lib/api/server';

function shortenFloats(v: unknown): unknown {
  if (Array.isArray(v)) return v.map(shortenFloats);
  if (v && typeof v === 'object') {
    return Object.fromEntries(
      Object.entries(v as Record<string, unknown>).map(([k, x]) => [k, shortenFloats(x)]),
    );
  }
  if (typeof v === 'number' && !Number.isInteger(v) && v % 1 === 0) return Math.trunc(v);
  return v;
}

function sortKeys(v: unknown): unknown {
  if (Array.isArray(v)) return v.map(sortKeys);
  if (v && typeof v === 'object') {
    return Object.keys(v as object)
      .sort()
      .reduce<Record<string, unknown>>((acc, k) => {
        acc[k] = sortKeys((v as Record<string, unknown>)[k]);
        return acc;
      }, {});
  }
  return v;
}

function hexToUint8Array(hex: string): Uint8Array {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = parseInt(hex.substr(i, 2), 16);
  }
  return bytes;
}

async function verifySignature(body: string, signature: string, secret: string): Promise<boolean> {
  try {
    const encoder = new TextEncoder();
    const parsed = JSON.parse(body);
    const canonical = JSON.stringify(sortKeys(shortenFloats(parsed)));

    const key = await crypto.subtle.importKey(
      'raw',
      encoder.encode(secret),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['verify']
    );

    const sigBytes = hexToUint8Array(signature);
    const dataBytes = encoder.encode(canonical);

    return await crypto.subtle.verify('HMAC', key, sigBytes as BufferSource, dataBytes);
  } catch {
    return false;
  }
}

export const POST: RequestHandler = async ({ request, platform }) => {
  const secret = platform?.env?.DIDIT_WEBHOOK_SECRET;
  const signature = request.headers.get('x-signature-v2') ?? '';
  const timestamp = Number(request.headers.get('x-timestamp'));
  const raw = await request.text();

  if (!secret || !signature) {
    return new Response(JSON.stringify({ error: 'Missing signature or secret' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  if (!timestamp || Math.abs(Date.now() / 1000 - timestamp) > 300) {
    return new Response('stale', { status: 401 });
  }

  const isValid = await verifySignature(raw, signature, secret);
  if (!isValid) {
    return new Response(JSON.stringify({ error: 'Invalid signature' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  let parsed: any;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return new Response(JSON.stringify({ error: 'Invalid JSON' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  const db = getDb({ platform });
  const now = Math.floor(Date.now() / 1000);

  const webhookType = parsed.webhook_type;
  const sessionId = parsed.session_id;
  const status = parsed.status;
  const vendorData = parsed.vendor_data;
  const eventId = parsed.event_id;
  const accountId = vendorData;

  if (!sessionId || !accountId) {
    return json({ received: true, note: 'Missing session_id or vendor_data' });
  }

  if (eventId) {
    const existing = await db.prepare(
      'SELECT 1 FROM lock_events WHERE id = ? LIMIT 1'
    ).bind(`WH-${eventId}`).first();
    if (existing) return json({ received: true, note: 'duplicate' });
    await db.prepare(
      'INSERT OR IGNORE INTO lock_events (id, account_id, event_type, triggered_by, created_at) VALUES (?, ?, ?, ?, ?)'
    ).bind(`WH-${eventId}`, accountId, `didit_${webhookType ?? 'unknown'}`, 'system', now).run();
  }

  switch (status) {
    case 'Approved': {
      const decision = parsed.decision;
      await db.prepare(`
        UPDATE accounts SET
          ghana_card_verified = 1,
          ghana_card_status = ?,
          ghana_card_verified_at = ?,
          didit_session_id = ?,
          didit_decision = ?,
          updated_at = ?
        WHERE id = ?
      `).bind(status, now, sessionId, decision ? JSON.stringify(decision) : null, now, accountId).run();

      const account = await db.prepare('SELECT customer_name, enrolled_by FROM accounts WHERE id = ?')
        .bind(accountId).first();
      if (account) {
        const recipients = await db.prepare(`
          SELECT id FROM dealers WHERE id = ? OR role = 'SUPER_ADMIN'
        `).bind(account.enrolled_by as string).all();
        for (const r of recipients.results) {
          await db.prepare(`
            INSERT INTO notifications (id, recipient_id, type, title, message, related_entity_type, related_entity_id, created_at)
            VALUES (?, ?, 'KYC_VERIFIED', 'Identity Verified', ?, 'account', ?, ?)
          `).bind(`NOTIF-${generateToken(4).toUpperCase()}`, r.id as string,
            `Customer ${account.customer_name as string} identity verification approved`, accountId, now).run();
        }
      }
      break;
    }

    case 'Declined': {
      await db.prepare(`
        UPDATE accounts SET
          ghana_card_verified = 0,
          ghana_card_status = ?,
          didit_session_id = ?,
          updated_at = ?
        WHERE id = ?
      `).bind(status, sessionId, now, accountId).run();
      break;
    }

    case 'In Review': {
      await db.prepare(`
        UPDATE accounts SET
          ghana_card_status = ?,
          didit_session_id = ?,
          updated_at = ?
        WHERE id = ?
      `).bind(status, sessionId, now, accountId).run();
      break;
    }

    case 'Resubmitted': {
      await db.prepare(`
        UPDATE accounts SET
          ghana_card_status = ?,
          didit_session_id = ?,
          updated_at = ?
        WHERE id = ?
      `).bind(status, sessionId, now, accountId).run();
      break;
    }

    case 'Kyc Expired': {
      await db.prepare(`
        UPDATE accounts SET
          ghana_card_verified = 0,
          ghana_card_status = ?,
          updated_at = ?
        WHERE id = ?
      `).bind(status, now, accountId).run();
      break;
    }

    case 'Abandoned': {
      await db.prepare(`
        UPDATE accounts SET ghana_card_status = ?, updated_at = ? WHERE id = ?
      `).bind(status, now, accountId).run();
      break;
    }

    default:
      break;
  }

  return json({ received: true, accountId, status });
};
