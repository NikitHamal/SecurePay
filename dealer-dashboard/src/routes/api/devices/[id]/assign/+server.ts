import { json } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { getDb, errorResponse } from '$lib/api/server';
import { logActivity } from '$lib/audit';

let ensureCols: Promise<void> | null = null;
function ensure(db: ReturnType<typeof getDb>): Promise<void> {
  if (!ensureCols) {
    ensureCols = (async () => {
      const info = await db.prepare('PRAGMA table_info(devices)').all();
      const existing = new Set(info.results.map((r: any) => String(r.name ?? '')));
      for (const [col, ddl] of [['assigned_to','TEXT'],['assigned_at','INTEGER'],['assigned_by','TEXT']] as const) {
        if (!existing.has(col)) await db.prepare(`ALTER TABLE devices ADD COLUMN ${col} ${ddl}`).run();
      }
    })().catch(e => { ensureCols=null; throw e; });
  }
  return ensureCols;
}

export const POST: RequestHandler = async ({ locals, params, request, platform }) => {
  if (!locals.dealer) return errorResponse('Unauthorized', 401);
  if (locals.dealer.role === 'AGENT') return errorResponse('Only admins can assign devices', 403);
  const body = await request.json().catch(() => null) as any;
  const agentId = body?.agentId ? String(body.agentId).trim() : body?.assignedTo ? String(body.assignedTo).trim() : '';
  const unassign = body?.unassign === true;
  if (!agentId && !unassign) return errorResponse('agentId required', 400);

  const db = getDb({ platform });
  await ensure(db);

  const device = await db.prepare('SELECT id, imei, model, status, assigned_to FROM devices WHERE id = ?').bind(params.id).first<any>();
  if (!device) return errorResponse('Device not found', 404);
  if (device.status !== 'in_stock') return errorResponse('Only in-stock devices can be (re)assigned', 409);

  if (unassign) {
    await db.prepare('UPDATE devices SET assigned_to = NULL, assigned_at = NULL, assigned_by = NULL WHERE id = ?').bind(params.id).run();
    await logActivity(db, { actor: locals.dealer!, action: 'DEVICE_UNASSIGNED', details: `Unassigned ${device.model} (${device.imei})`, imei: device.imei });
    return json({ success: true, id: params.id, assignedTo: null });
  }

  const agent = await db.prepare('SELECT id, name, role, agency_id, branch_id FROM dealers WHERE id = ?').bind(agentId).first<any>();
  if (!agent) return errorResponse('Agent not found', 404);
  if (agent.role !== 'AGENT') return errorResponse('Can only assign to AGENTs', 400);

  // Optional scope check: agency/branch admins can only assign within their scope.
  if (locals.dealer.role !== 'SUPER_ADMIN') {
    const mismatch = (locals.dealer.role === 'AGENCY_OWNER' && locals.dealer.agencyId && agent.agency_id !== locals.dealer.agencyId)
      || (locals.dealer.role === 'BRANCH_ADMIN' && locals.dealer.branchId && agent.branch_id !== locals.dealer.branchId);
    if (mismatch) return errorResponse('Agent is outside your agency/branch scope', 403);
  }

  const now = Math.floor(Date.now() / 1000);
  await db.prepare('UPDATE devices SET assigned_to = ?, assigned_at = ?, assigned_by = ? WHERE id = ?').bind(agentId, now, locals.dealer.id, params.id).run();
  await logActivity(db, { actor: locals.dealer!, action: 'DEVICE_ASSIGNED', details: `Assigned ${device.model} (${device.imei}) to ${agent.name}`, imei: device.imei });
  return json({ success: true, id: params.id, assignedTo: agentId, assignedAt: now * 1000 });
};
