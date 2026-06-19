import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';

export const DELETE: RequestHandler = async ({ locals, params, platform, url }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  const db = getDb({ platform });
  const device = await db.prepare(`
    SELECT d.id, d.status, a.id AS account_id
    FROM devices d
    LEFT JOIN accounts a ON a.device_id = d.id
    WHERE d.id = ? AND d.dealer_id = ?
  `).bind(params.id, locals.dealer.id).first<{ id: string; status: string; account_id?: string | null }>();

  if (!device) {
    return errorResponse('Device not found', 404);
  }

  const force = url.searchParams.get('force') === 'true';
  if (device.account_id && !force) {
    return errorResponse('Device is assigned to a customer. Delete the customer first, or retry with force=true.', 409);
  }

  if (device.account_id) {
    await db.batch([
      db.prepare('DELETE FROM provisioning_tokens WHERE account_id = ?').bind(device.account_id),
      db.prepare('DELETE FROM payments WHERE account_id = ?').bind(device.account_id),
      db.prepare('DELETE FROM lock_events WHERE account_id = ?').bind(device.account_id),
      db.prepare('DELETE FROM accounts WHERE id = ? AND dealer_id = ?').bind(device.account_id, locals.dealer.id),
      db.prepare('DELETE FROM devices WHERE id = ? AND dealer_id = ?').bind(params.id, locals.dealer.id)
    ]);
  } else {
    await db.batch([
      db.prepare('DELETE FROM provisioning_tokens WHERE device_id = ?').bind(params.id),
      db.prepare('DELETE FROM devices WHERE id = ? AND dealer_id = ?').bind(params.id, locals.dealer.id)
    ]);
  }

  return json({ success: true, id: params.id });
};
