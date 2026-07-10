import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { getDealerScopeFilter, canReleaseOrDeleteAccount } from '$lib/auth';

export const DELETE: RequestHandler = async ({ locals, params, platform, url }) => {
  if (!locals.dealer) {
    return errorResponse('Unauthorized', 401);
  }

  if (!canReleaseOrDeleteAccount(locals.dealer.role)) {
    return errorResponse('Only branch admins and above can delete inventory', 403);
  }

  const db = getDb({ platform });
  const scope = getDealerScopeFilter(locals.dealer, 'owner');
  const device = await db.prepare(`
    SELECT d.id, d.status, a.id AS account_id
    FROM devices d
    JOIN dealers owner ON owner.id = d.dealer_id
    LEFT JOIN accounts a ON a.device_id = d.id
    WHERE d.id = ? AND ${scope.where}
  `).bind(params.id, ...scope.params).first<{ id: string; status: string; account_id?: string | null }>();

  if (!device) {
    return errorResponse('Device not found', 404);
  }

  const force = url.searchParams.get('force') === 'true';
  if (device.account_id && !force) {
    return errorResponse('Device is assigned to a customer. Delete the customer first, or retry with force=true.', 409);
  }

  if (device.account_id) {
    await db.batch([
      db.prepare('DELETE FROM location_logs WHERE account_id = ?').bind(device.account_id),
      db.prepare('DELETE FROM provisioning_tokens WHERE account_id = ?').bind(device.account_id),
      db.prepare('DELETE FROM payments WHERE account_id = ?').bind(device.account_id),
      db.prepare('DELETE FROM lock_events WHERE account_id = ?').bind(device.account_id),
      db.prepare('DELETE FROM accounts WHERE id = ?').bind(device.account_id),
      db.prepare('DELETE FROM devices WHERE id = ?').bind(params.id)
    ]);
  } else {
    await db.batch([
      db.prepare('DELETE FROM provisioning_tokens WHERE device_id = ?').bind(params.id),
      db.prepare('DELETE FROM devices WHERE id = ?').bind(params.id)
    ]);
  }

  return json({ success: true, id: params.id });
};
